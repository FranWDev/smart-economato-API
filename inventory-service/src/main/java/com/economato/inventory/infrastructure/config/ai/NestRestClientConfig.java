package com.economato.inventory.infrastructure.config.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class NestRestClientConfig {

    @Bean
    @Qualifier("nestRestClient")
    public RestClient nestRestClient(RestClient.Builder builder, AiNestProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.getConnectionTimeoutMs()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.getReadTimeoutMs()));

        return builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Key", properties.getServiceKey())
                .build();
    }
}
