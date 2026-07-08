package com.economato.inventory.infrastructure.config.ai.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ai.nest")
public class AiNestProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String serviceKey;

    @NotBlank
    private String allowedOrigin;

    @NotNull
    @Min(5000)
    private Long streamTimeoutMs = 120000L;

    @NotNull
    @Min(1000)
    private Long connectionTimeoutMs = 5000L;

    @NotNull
    @Min(1000)
    private Long readTimeoutMs = 60000L;

    @NotNull
    @Min(1)
    private Integer maxRetries = 2;

    private String completionEndpoint = "/api/completion";
}