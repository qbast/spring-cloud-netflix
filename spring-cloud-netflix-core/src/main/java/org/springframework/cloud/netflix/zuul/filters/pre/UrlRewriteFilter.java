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

package org.springframework.cloud.netflix.zuul.filters.pre;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import lombok.Data;

import org.springframework.util.ReflectionUtils;

/**
 * @author Spencer Gibb
 */
public class UrlRewriteFilter implements Filter {

	private final UrlRewriteProperties properties;

	private AtomicBoolean initialized = new AtomicBoolean(false);

	private org.tuckey.web.filters.urlrewrite.UrlRewriteFilter delegate = new org.tuckey.web.filters.urlrewrite.UrlRewriteFilter();

	public UrlRewriteFilter(UrlRewriteProperties properties) {
		this.properties = properties;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {  }

	@Override
	public void destroy() {
		delegate.destroy();
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		if (initialized.compareAndSet(false, true)) {
			RewriteFilterConfig filterConfig = new RewriteFilterConfig(properties);
			filterConfig.setServletContext(request.getServletContext());
			delegate.init(filterConfig);
		}

		delegate.doFilter(request, response, chain);

		chain.doFilter(request, response);

	}

	protected org.tuckey.web.filters.urlrewrite.UrlRewriteFilter getDelegate() {
		return delegate;
	}

	@Data
	private class RewriteFilterConfig implements FilterConfig {
		private ServletContext servletContext;
		private String filterName = "UrlRewriteFilter";
		private final UrlRewriteProperties urlRewriteProperties;

		@Override
		public String getInitParameter(String name) {
			Field field = ReflectionUtils.findField(urlRewriteProperties.getClass(), name);
			if (field == null) {
				return null;
			}
			ReflectionUtils.makeAccessible(field);
			Object value = ReflectionUtils.getField(field, urlRewriteProperties);
			if (value instanceof String) {
				return (String) value;
			}
			return null;
		}

		@Override
		public Enumeration<String> getInitParameterNames() {
			List<String> fields = new ArrayList<>();
			for (Field field : urlRewriteProperties.getClass().getFields()) {
				fields.add(field.getName());
			}
			return Collections.enumeration(fields);
		}
	}
}
