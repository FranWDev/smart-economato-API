package com.economato.inventory.infrastructure.config.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiAnalysisPropertiesTest {

    @Test
    void reorderHorizonDays_default14() {
        AiAnalysisProperties properties = new AiAnalysisProperties();

        assertEquals(14, properties.getReorderSuggestionHorizonDays());
    }

    @Test
    void wasteRiskDaysThreshold_default7() {
        AiAnalysisProperties properties = new AiAnalysisProperties();

        assertEquals(7, properties.getWasteRiskDaysThreshold());
    }

    @Test
    void defaults_arePositive() {
        AiAnalysisProperties properties = new AiAnalysisProperties();

        assertEquals(90, properties.getCostBreakdownMaxDays());
        assertEquals(50, properties.getMenuOptimizerMaxRecipes());
    }
}