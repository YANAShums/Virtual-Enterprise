package com.virtualenterprise.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {
    @Bean
    RestClient workloadRestClient(RestClient.Builder builder,
                                  @Value("${workload.base-url}") String baseUrl,
                                  @Value("${workload.service-token}") String token) {
        return builder.baseUrl(baseUrl)
                .defaultHeader("X-Service-Token", token)
                .build();
    }
}
