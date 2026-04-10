package com.economato.inventory.infrastructure.config.security;

import jakarta.validation.constraints.NotBlank;
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
@ConfigurationProperties(prefix = "ledger")
public class LedgerProperties {

    @NotBlank(message = "{validation.ledgerProperties.hmacSecret.notBlank}")
    private String hmacSecret;

    private Integer currentHmacVersion = 1;

    private Map<Integer, String> hmacSecretVersions = new HashMap<>();

    public String getHmacSecretForVersion(int version) {
        if (hmacSecretVersions == null || hmacSecretVersions.isEmpty()) {
            return hmacSecret;
        }

        String secret = hmacSecretVersions.get(version);
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("No HMAC secret configured for ledger version: " + version);
        }
        return secret;
    }
}
