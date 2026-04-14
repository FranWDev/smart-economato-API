package com.economato.inventory.infrastructure.config.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ai.providers")
public class AiProviderProperties {

    private Map<String, ProviderConfig> configs = new HashMap<>();

    @Getter
    @Setter
    public static class ProviderConfig {
        private String displayName;
        private String keyPrefix;
        private Boolean enabled = true;
        private String modelDefault;
        private Integer maxContextTokens;
    }
}