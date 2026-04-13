package com.economato.inventory.infrastructure.config.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class AiProviderPropertiesTest {

    private AiProviderProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiProviderProperties();

        AiProviderProperties.ProviderConfig openai = new AiProviderProperties.ProviderConfig();
        openai.setDisplayName("OpenAI");
        openai.setEnabled(true);

        AiProviderProperties.ProviderConfig anthropic = new AiProviderProperties.ProviderConfig();
        anthropic.setDisplayName("Anthropic");
        anthropic.setEnabled(false);

        properties.setConfigs(Map.of(
                "OPENAI", openai,
                "ANTHROPIC", anthropic
        ));
    }

    @Test
    void getEnabledProviders_filtersDisabledProviders() {
        long enabled = properties.getConfigs().values().stream()
                .filter(config -> !Boolean.FALSE.equals(config.getEnabled()))
                .count();

        assertEquals(1, enabled);
    }

    @Test
    void getConfig_forExistingProvider_returnsConfig() {
        AiProviderProperties.ProviderConfig config = properties.getConfigs().get("OPENAI");

        assertEquals("OpenAI", config.getDisplayName());
    }

    @Test
    void getConfig_forNonExistentProvider_returnsNull() {
        AiProviderProperties.ProviderConfig config = properties.getConfigs().get("MISTRAL");

        assertNull(config);
    }
}
