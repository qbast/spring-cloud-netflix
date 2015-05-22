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

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collection;
import java.util.Map;

/**
 * @author Dave Syer
 */
public interface RouteLocator {
	/**
	 * Map of paths to locations
	 * @return
	 */
	public Map<String, String> getRouteLocations();

	Collection<String> getRoutePaths();

	public RouteSpec getMatchingRoute(String path);

	@Data
	@AllArgsConstructor
	public static class RouteSpec {

		private String id;

		private String path;

		private String location;

		private String prefix;

		private Boolean retryable;

	}
}
