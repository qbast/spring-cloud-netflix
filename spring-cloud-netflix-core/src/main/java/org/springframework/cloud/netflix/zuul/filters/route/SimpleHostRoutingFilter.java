/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.netflix.zuul.filters.route;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.apachecommons.CommonsLog;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.context.RequestContext;

@CommonsLog
public class SimpleHostRoutingFilter extends ZuulFilter {

	public static final String CONTENT_ENCODING = "Content-Encoding";

	private final Runnable clientloader = new Runnable() {
		@Override
		public void run() {
			loadClient();
		}
	};

	private final DynamicIntProperty socketTimeout = DynamicPropertyFactory
			.getInstance().getIntProperty(ZuulConstants.ZUUL_HOST_SOCKET_TIMEOUT_MILLIS,
					10000);

	private final DynamicIntProperty connectionTimeout = DynamicPropertyFactory
			.getInstance().getIntProperty(ZuulConstants.ZUUL_HOST_CONNECT_TIMEOUT_MILLIS,
					2000);

	private final AtomicReference<HttpClient> client = new AtomicReference<>();

	private final Timer connectionManagerTimer = new Timer(
			"SimpleHostRoutingFilter.connectionManagerTimer", true);

	private ProxyRequestHelper helper;

	@Autowired(required = false)
	private LayeredConnectionSocketFactory sslSocketFactory;

	@Autowired(required = false)
	private X509HostnameVerifier hostnameVerifier;

	public SimpleHostRoutingFilter() {
		this(new ProxyRequestHelper());
	}

	public SimpleHostRoutingFilter(ProxyRequestHelper helper) {
		this.helper = helper;
	}

	@PostConstruct
	public void init() {
		client.set(newClient());
		// cleans expired connections at an interval
		socketTimeout.addCallback(clientloader);
		connectionTimeout.addCallback(clientloader);
		connectionManagerTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					final HttpClient hc = client.get();
					if (hc == null) {
						return;
					}
					hc.getConnectionManager().closeExpiredConnections();
				} catch (Throwable ex) {
					log.error("error closing expired connections", ex);
				}
			}
		}, 30000, 5000);
	}

	@PreDestroy
	public void stop() {
		connectionManagerTimer.cancel();
	}

	@Override
	public String filterType() {
		return "route";
	}

	@Override
	public int filterOrder() {
		return 100;
	}

	@Override
	public boolean shouldFilter() {
		return RequestContext.getCurrentContext().getRouteHost() != null
				&& RequestContext.getCurrentContext().sendZuulResponse();
	}

	@Override
	public Object run() {
		RequestContext context = RequestContext.getCurrentContext();
		HttpServletRequest request = context.getRequest();
		MultiValueMap<String, String> headers = this.helper
				.buildZuulRequestHeaders(request);
		MultiValueMap<String, String> params = this.helper
				.buildZuulRequestQueryParams(request);
		String verb = getVerb(request);
		InputStream requestEntity = getRequestBody(request);
		HttpClient httpclient = client.get();

		String uri = request.getRequestURI();
		if (context.get("requestURI") != null) {
			uri = (String) context.get("requestURI");
		}

		try {
			HttpResponse response = forward(httpclient, verb, uri, request, headers,
					params, requestEntity);
			setResponse(response);
		}
		catch (Exception ex) {
			context.set("error.status_code", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			context.set("error.exception", ex);
		}
		return null;
	}

	private HttpResponse forward(HttpClient httpclient, String verb, String uri,
			HttpServletRequest request, MultiValueMap<String, String> headers,
			MultiValueMap<String, String> params, InputStream requestEntity)
			throws Exception {
		Map<String, Object> info = this.helper.debug(verb, uri, headers, params,
				requestEntity);
		URL host = RequestContext.getCurrentContext().getRouteHost();
		HttpHost httpHost = getHttpHost(host);
		uri = StringUtils.cleanPath(host.getPath() + uri);
		HttpRequest httpRequest;
		switch (verb.toUpperCase()) {
		case "POST":
			HttpPost httpPost = new HttpPost(uri + getQueryString());
			httpRequest = httpPost;
			httpPost.setEntity(new InputStreamEntity(requestEntity, request
					.getContentLength()));
			break;
		case "PUT":
			HttpPut httpPut = new HttpPut(uri + getQueryString());
			httpRequest = httpPut;
			httpPut.setEntity(new InputStreamEntity(requestEntity, request
					.getContentLength()));
			break;
		case "PATCH":
			HttpPatch httpPatch = new HttpPatch(uri + getQueryString());
			httpRequest = httpPatch;
			httpPatch.setEntity(new InputStreamEntity(requestEntity, request
					.getContentLength()));
			break;
		default:
			httpRequest = new BasicHttpRequest(verb, uri + getQueryString());
			log.debug(uri + getQueryString());
		}
		try {
			httpRequest.setHeaders(convertHeaders(headers));
			log.debug(httpHost.getHostName() + " " + httpHost.getPort() + " "
					+ httpHost.getSchemeName());
			HttpResponse zuulResponse = forwardRequest(httpclient, httpHost, httpRequest);
			this.helper.appendDebug(info, zuulResponse.getStatusLine().getStatusCode(),
					revertHeaders(zuulResponse.getAllHeaders()));
			return zuulResponse;
		}
		finally {
			// When HttpClient instance is no longer needed,
			// shut down the connection manager to ensure
			// immediate deallocation of all system resources
			// httpclient.getConnectionManager().shutdown();
		}
	}

	private MultiValueMap<String, String> revertHeaders(Header[] headers) {
		MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
		for (Header header : headers) {
			String name = header.getName();
			if (!map.containsKey(name)) {
				map.put(name, new ArrayList<String>());
			}
			map.get(name).add(header.getValue());
		}
		return map;
	}

	private Header[] convertHeaders(MultiValueMap<String, String> headers) {
		List<Header> list = new ArrayList<>();
		for (String name : headers.keySet()) {
			for (String value : headers.get(name)) {
				list.add(new BasicHeader(name, value));
			}
		}
		return list.toArray(new BasicHeader[0]);
	}

	private HttpResponse forwardRequest(HttpClient httpclient, HttpHost httpHost,
			HttpRequest httpRequest) throws IOException {
		return httpclient.execute(httpHost, httpRequest);
	}

	private String getQueryString() {
		HttpServletRequest request = RequestContext.getCurrentContext().getRequest();
		String query = request.getQueryString();
		return (query != null) ? "?" + query : "";
	}

	private HttpHost getHttpHost(URL host) {
		HttpHost httpHost = new HttpHost(host.getHost(), host.getPort(),
				host.getProtocol());
		return httpHost;
	}

	private InputStream getRequestBody(HttpServletRequest request) {
		InputStream requestEntity = null;
		try {
			requestEntity = request.getInputStream();
		}
		catch (IOException ex) {
			// no requestBody is ok.
		}
		return requestEntity;
	}

	private String getVerb(HttpServletRequest request) {
		String sMethod = request.getMethod();
		return sMethod.toUpperCase();
	}

	private void setResponse(HttpResponse response) throws IOException {
		this.helper.setResponse(response.getStatusLine().getStatusCode(),
				response.getEntity() == null ? null : response.getEntity().getContent(),
				revertHeaders(response.getAllHeaders()));
	}

	private void loadClient() {
		final HttpClient oldClient = client.get();
		client.set(newClient());
		if (oldClient != null) {
			connectionManagerTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					try {
						oldClient.getConnectionManager().shutdown();
					}
					catch (Throwable ex) {
						log.error("error shutting down old connection manager", ex);
					}
				}
			}, 30000);
		}
	}

	private HttpClient newClient() {
		try {
			HttpClientBuilder builder = HttpClients
					.custom()
					.setDefaultRequestConfig(
							RequestConfig.custom().setSocketTimeout(socketTimeout.get())
									.setConnectTimeout(connectionTimeout.get())
									.setCookieSpec(CookieSpecs.IGNORE_COOKIES).build())
					.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
					.setRedirectStrategy(new RedirectStrategy() {

						@Override
						public boolean isRedirected(HttpRequest request,
													HttpResponse response, HttpContext context)
								throws ProtocolException {
							return false;
						}

						@Override
						public HttpUriRequest getRedirect(HttpRequest request,
														  HttpResponse response, HttpContext context)
								throws ProtocolException {
							return null;
						}
					})
					.setMaxConnTotal(
							Integer.parseInt(System.getProperty(
									"zuul.max.host.connections", "200")))
					.setMaxConnPerRoute(
							Integer.parseInt(System.getProperty(
									"zuul.max.host.connections", "20")))
					.useSystemProperties();

			if (sslSocketFactory != null) {
				builder.setSSLSocketFactory(sslSocketFactory);
			}
			if (hostnameVerifier != null) {
				builder.setHostnameVerifier(hostnameVerifier);
			}
			return builder.build();
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

}
