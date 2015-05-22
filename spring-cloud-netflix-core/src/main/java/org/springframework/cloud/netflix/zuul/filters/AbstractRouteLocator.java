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

package org.springframework.cloud.netflix.zuul.filters;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * @author Spencer Gibb
 */
@CommonsLog
public abstract class AbstractRouteLocator implements RouteLocator {

	protected PathMatcher pathMatcher = new AntPathMatcher();
	protected ZuulProperties properties;
	protected String servletPath;

	public AbstractRouteLocator(ZuulProperties properties, String servletPath) {
		this.properties = properties;
		if (StringUtils.hasText(servletPath)) { // a servletPath is passed explicitly
	        this.servletPath = servletPath;
	    } else {
	        //set Zuul servlet path
	        this.servletPath = properties.getServletPath() != null? properties.getServletPath() : "";
	    }
	}

	protected abstract Map<String, ZuulProperties.ZuulRoute> getZuulRoutes();

	public RouteSpec getMatchingRoute(String path) {
		log.info("Finding route for path: " + path);

		String location = null;
		String targetPath = null;
		String id = null;
		String prefix = this.properties.getPrefix();
		log.debug("servletPath=" + this.servletPath);
		if (StringUtils.hasText(this.servletPath) && !this.servletPath.equals("/")
				&& path.startsWith(this.servletPath)) {
			path = path.substring(this.servletPath.length());
		}
		log.debug("path=" + path);
		Boolean retryable = this.properties.getRetryable();
		for (Map.Entry<String, ZuulProperties.ZuulRoute> entry : getZuulRoutes().entrySet()) {
			String pattern = entry.getKey();
			log.debug("Matching pattern:" + pattern);
			if (this.pathMatcher.match(pattern, path)) {
				ZuulProperties.ZuulRoute route = entry.getValue();
				id = route.getId();
				location = route.getLocation();
				targetPath = path;
				if (path.startsWith(prefix) && this.properties.isStripPrefix()) {
					targetPath = path.substring(prefix.length());
				}
				if (route.isStripPrefix()) {
					int index = route.getPath().indexOf("*") - 1;
					if (index > 0) {
						String routePrefix = route.getPath().substring(0, index);
						targetPath = targetPath.replaceFirst(routePrefix, "");
						prefix = prefix + routePrefix;
					}
				}
				if (route.getRetryable() != null) {
					retryable = route.getRetryable();
				}
				break;
			}
		}
		return (location == null ? null : new RouteSpec(id, targetPath, location,
				prefix, retryable));
	}


	protected void addConfiguredRoutes(Map<String, ZuulProperties.ZuulRoute> routes) {
		for (ZuulProperties.ZuulRoute entry : this.properties.getRoutes().values()) {
			String route = entry.getPath();
			if (routes.containsKey(route)) {
				log.warn("Overwriting route " + route + ": already defined by "
						+ routes.get(route));
			}
			routes.put(route, entry);
		}
	}

}
