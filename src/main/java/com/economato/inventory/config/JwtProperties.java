package com.economato.inventory.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    @NotBlank(message = "{validation.jwtProperties.secret.notBlank}")
    private String secret;

    @Positive(message = "{validation.jwtProperties.expiration.positive}")
    private long expiration;
}
