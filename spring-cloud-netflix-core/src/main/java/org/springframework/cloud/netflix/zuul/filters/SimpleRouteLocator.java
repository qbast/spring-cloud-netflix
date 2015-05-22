/*
 * Copyright 2013-2014 the original author or authors.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.cloud.netflix.zuul.filters.ZuulProperties.ZuulRoute;

/**
 * @author Dave Syer
 */
@CommonsLog
public class SimpleRouteLocator extends AbstractRouteLocator {

	public SimpleRouteLocator(ZuulProperties properties, String servletPath) {
		super(properties, servletPath);
	}

	@Override
	public Map<String, String> getRouteLocations() {
		Map<String, String> values = new LinkedHashMap<>();
		Map<String, ZuulRoute> zuulRoutes = getZuulRoutes();
		for (String key : zuulRoutes.keySet()) {
			values.put(key, zuulRoutes.get(key).getLocation());
		}
		return values;
	}

	@Override
	public Collection<String> getRoutePaths() {
		Collection<String> paths = new LinkedHashSet<>();
		for (ZuulRoute route : this.properties.getRoutes().values()) {
			paths.add(route.getPath());
		}
		return paths;
	}

	@Override
	protected Map<String, ZuulRoute> getZuulRoutes() {
		Map<String, ZuulRoute> routes = new HashMap<>();
		addConfiguredRoutes(routes);
		return routes;
	}


}
