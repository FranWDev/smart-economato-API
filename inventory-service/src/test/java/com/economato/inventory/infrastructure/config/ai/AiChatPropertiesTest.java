package com.economato.inventory.infrastructure.config.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatPropertiesTest {

    @Test
    void defaultLanguage_isEs() {
        AiChatProperties properties = new AiChatProperties();

        assertEquals("es", properties.getDefaultLanguage());
    }

    @Test
    void supportedLanguages_containsDefaults() {
        AiChatProperties properties = new AiChatProperties();

        assertTrue(properties.getSupportedLanguages().contains("es"));
        assertTrue(properties.getSupportedLanguages().contains("en"));
        assertTrue(properties.getSupportedLanguages().contains("fr"));
    }

    @Test
    void maxConcurrentStreamsPerUser_default() {
        AiChatProperties properties = new AiChatProperties();

        assertEquals(2, properties.getMaxConcurrentStreamsPerUser());
    }
}