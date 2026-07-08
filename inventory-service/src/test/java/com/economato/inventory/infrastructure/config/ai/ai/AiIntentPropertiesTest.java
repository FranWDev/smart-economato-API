package com.economato.inventory.infrastructure.config.ai.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiIntentPropertiesTest {

    @Test
    void patterns_loadFromProperties() {
        AiIntentProperties properties = new AiIntentProperties();

        assertFalse(properties.getPatterns().isEmpty());
    }

    @Test
    void stockCheck_hasExpectedKeywords() {
        AiIntentProperties properties = new AiIntentProperties();

        List<String> keywords = properties.getPatterns().get("STOCK_CHECK");
        assertTrue(keywords.contains("stock"));
        assertTrue(keywords.contains("inventario"));
    }

    @Test
    void allIntents_haveAtLeastOneKeyword() {
        AiIntentProperties properties = new AiIntentProperties();

        assertTrue(properties.getPatterns().values().stream().allMatch(list -> list != null && !list.isEmpty()));
    }
}