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

package org.springframework.cloud.netflix.sidecar;

import com.netflix.http4.ssl.AcceptAllSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.metrics.repository.InMemoryMetricRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableSidecar
@EnableDiscoveryClient
@RestController
public class SidecarApplication {

	@Bean
	public InMemoryMetricRepository inMemoryMetricRepository() {
		return new InMemoryMetricRepository();
	}

	@Bean
	public X509HostnameVerifier allowAllHostnameVerifier() {
		return SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
	}

	@Bean
	public AcceptAllSocketFactory acceptAllSocketFactory() throws Exception {
		return new AcceptAllSocketFactory();
	}

	public static void main(String[] args) {
		SpringApplication.run(SidecarApplication.class, args);
	}

}
