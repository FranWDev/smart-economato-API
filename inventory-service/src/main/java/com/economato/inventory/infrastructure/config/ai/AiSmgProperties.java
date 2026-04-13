package com.economato.inventory.infrastructure.config.ai;

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
@ConfigurationProperties(prefix = "ai.smg")
public class AiSmgProperties {

    @NotNull
    @Min(1000)
    private Integer tokenBudget = 6000;

    @NotNull
    private Double workingMemoryWeight = 0.57;

    @NotNull
    private Double entityMemoryWeight = 0.20;

    @NotNull
    private Double topicMemoryWeight = 0.10;

    @NotNull
    private Double intentMemoryWeight = 0.03;

    @NotNull
    private Double systemContextWeight = 0.10;

    @NotNull
    private Double decayLambda = 3.0;

    @NotNull
    private Double decayFullThreshold = 0.7;

    @NotNull
    private Double decayOnelinerThreshold = 0.3;

    @NotNull
    @Min(1)
    private Integer topicSplitGapMinutes = 5;

    @NotNull
    private Double topicEntityChangeThreshold = 0.7;

    @NotNull
    @Min(1)
    private Integer entityCacheTtlSeconds = 30;

    @NotNull
    @Min(1)
    private Integer catalogCacheTtlSeconds = 300;

    @NotNull
    @Min(10)
    private Integer maxWorkingMemoryMessages = 30;

    @NotNull
    @Min(50)
    private Integer toolResultMaxChars = 500;

    @NotNull
    @Min(4)
    private Integer tokenEstimationDivisor = 4;
}