package com.economato.inventory.infrastructure.config.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {

    @NotNull
    @Min(1)
    private Integer blockSize = 10;

    @NotNull
    @Min(1000)
    private Long miningIntervalMs = 30000L;

    @NotNull
    @Min(0)
    private Integer difficulty = 2;
}
