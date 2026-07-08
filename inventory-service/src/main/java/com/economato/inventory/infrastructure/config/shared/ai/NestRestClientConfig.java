package com.economato.inventory.infrastructure.config.shared.ai;
import com.economato.inventory.infrastructure.config.ai.ai.AiNestProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class NestRestClientConfig {

    @Bean
    @Qualifier("nestRestClient")
    public RestClient nestRestClient(AiNestProperties properties) {
        warnIfInsecureRemoteBaseUrl(properties.getBaseUrl());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.getConnectionTimeoutMs()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.getReadTimeoutMs()));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Key", properties.getServiceKey())
                .build();
    }

    private void warnIfInsecureRemoteBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            boolean localHost = "localhost".equals(host) || "127.0.0.1".equals(host);
            if ("http".equals(scheme) && !localHost) {
                log.warn("ai.nest.base-url is using plain HTTP for non-local host ({}). API keys may travel unencrypted.", baseUrl);
            }
        } catch (Exception ignored) {
            // URL validation happens through configuration binding and usage.
        }
    }
}
