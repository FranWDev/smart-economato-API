package com.economato.inventory.infrastructure.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ai.analysis")
public class AiAnalysisProperties {

    @NotNull
    @Min(1)
    private Integer wasteRiskDaysThreshold = 7;

    @NotNull
    @Min(1)
    private Integer reorderSuggestionHorizonDays = 14;

    @NotNull
    @Min(1)
    private Integer costBreakdownMaxDays = 90;

    @NotNull
    @Min(1)
    private Integer menuOptimizerMaxRecipes = 50;

    @NotNull
    private Double stockHealthStockWeight = 0.5;

    @NotNull
    private Double stockHealthBatchWeight = 0.3;

    @NotNull
    private Double stockHealthAlertWeight = 0.2;
}