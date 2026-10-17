package com.virtualenterprise.app.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(MlClientProperties.class)
public class RestClientConfig {
    @Bean
    RestClient mlAdministrationRestClient(RestClient.Builder builder, MlClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(java.time.Duration.ofMinutes(3));
        return builder.clone().baseUrl(properties.getBaseUrl())
                .defaultHeader("X-Service-Token", properties.getServiceToken()).requestFactory(factory).build();
    }
    @Bean
    RestClient mlRestClient(RestClient.Builder builder,
                            MlClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return builder.clone().baseUrl(properties.getBaseUrl())
                .defaultHeader("X-Service-Token", properties.getServiceToken())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    RestClient workloadRestClient(RestClient.Builder builder,
                                  @org.springframework.beans.factory.annotation.Value("${workload.base-url}") String baseUrl,
                                  @org.springframework.beans.factory.annotation.Value("${workload.service-token}") String token) {
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));
        return builder.clone().baseUrl(baseUrl)
                .defaultHeader("X-Service-Token", token)
                .requestFactory(factory)
                .build();
    }
}
