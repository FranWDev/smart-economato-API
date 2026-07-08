package com.economato.inventory.infrastructure.config.ai.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

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
    void stockHealthWeights_sumToOne() {
        AiAnalysisProperties properties = new AiAnalysisProperties();

        double sum = properties.getStockHealthStockWeight()
                + properties.getStockHealthBatchWeight()
                + properties.getStockHealthAlertWeight();

        assertTrue(Math.abs(1.0 - sum) <= 0.0001);
    }

    @Test
    void defaults_arePositive() {
        AiAnalysisProperties properties = new AiAnalysisProperties();

        assertEquals(90, properties.getCostBreakdownMaxDays());
        assertEquals(50, properties.getMenuOptimizerMaxRecipes());
    }
}