package com.economato.inventory.infrastructure.config.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ledger")
public class LedgerProperties {

    @NotBlank(message = "{validation.ledgerProperties.hmacSecret.notBlank}")
    private String hmacSecret;
}
