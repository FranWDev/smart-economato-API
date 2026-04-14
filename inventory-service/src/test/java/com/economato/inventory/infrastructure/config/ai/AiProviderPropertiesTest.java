package com.economato.inventory.infrastructure.config.ai;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiProviderPropertiesTest {

    private AiProviderProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiProviderProperties();

        AiProviderProperties.ProviderConfig openai = new AiProviderProperties.ProviderConfig();
        openai.setDisplayName("OpenAI");
        openai.setKeyPrefix("sk-");
        openai.setEnabled(true);
        openai.setModelDefault("gpt-4o");
        openai.setMaxContextTokens(128000);

        AiProviderProperties.ProviderConfig anthropic = new AiProviderProperties.ProviderConfig();
        anthropic.setDisplayName("Anthropic");
        anthropic.setKeyPrefix("sk-ant-");
        anthropic.setEnabled(false);
        anthropic.setModelDefault("claude-3.5-sonnet");
        anthropic.setMaxContextTokens(200000);

        properties.setConfigs(Map.of(
                "OPENAI", openai,
                "ANTHROPIC", anthropic
        ));
    }

    @Test
    void configs_loadCorrectly() {
        assertEquals(2, properties.getConfigs().size());
        assertTrue(properties.getConfigs().containsKey("OPENAI"));
        assertTrue(properties.getConfigs().containsKey("ANTHROPIC"));
    }

    @Test
    void enabledProvider_returnsConfig() {
        AiProviderProperties.ProviderConfig config = properties.getConfigs().get("OPENAI");

        assertTrue(Boolean.TRUE.equals(config.getEnabled()));
        assertEquals("gpt-4o", config.getModelDefault());
    }

    @Test
    void disabledProvider_isFilteredOut() {
        long enabled = properties.getConfigs().values().stream()
                .filter(config -> !Boolean.FALSE.equals(config.getEnabled()))
                .count();

        assertEquals(1, enabled);
    }

    @Test
    void keyPrefix_matchesProvider() {
        assertEquals("sk-", properties.getConfigs().get("OPENAI").getKeyPrefix());
        assertEquals("sk-ant-", properties.getConfigs().get("ANTHROPIC").getKeyPrefix());
    }

    @Test
    void maxContextTokens_isPositive() {
        assertTrue(properties.getConfigs().values().stream()
                .allMatch(config -> config.getMaxContextTokens() != null && config.getMaxContextTokens() > 0));
    }
}
