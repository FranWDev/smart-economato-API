package com.economato.inventory.infrastructure.config.ai;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@ConfigurationProperties(prefix = "ai.vault")
public class AiVaultProperties {

    @NotBlank
    private String masterKey;

    @NotNull
    @Min(1)
    private Integer currentKeyVersion = 1;

    private Map<Integer, String> keyVersions = new HashMap<>();

    public String getKeyForVersion(int version) {
        if (keyVersions == null || keyVersions.isEmpty()) {
            return masterKey;
        }

        String key = keyVersions.get(version);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("No vault key configured for version: " + version);
        }
        return key;
    }
}