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

    @NotBlank(message = "El secreto JWT no puede estar vacío")
    private String secret;

    @Positive(message = "La expiración JWT debe ser un valor positivo")
    private long expiration;
}
