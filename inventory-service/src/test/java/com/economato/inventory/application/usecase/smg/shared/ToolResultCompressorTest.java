package com.economato.inventory.application.usecase.smg.shared;

import com.economato.inventory.application.usecase.smg.model.shared.EntityMemory;
import com.economato.inventory.infrastructure.config.ai.ai.AiSmgProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultCompressorTest {

    private ToolResultCompressor compressor;

    @BeforeEach
    void setUp() {
        AiSmgProperties properties = new AiSmgProperties();
        properties.setToolResultMaxChars(40);
        compressor = new ToolResultCompressor(properties, new ObjectMapper());
    }

    @Test
    void compress_blankInput_returnsEmpty() {
        assertEquals("", compressor.compress("  ", new EntityMemory()));
    }

    @Test
    void compress_json_removesEmptyFieldsAndLimitsLargeArrays() {
        String json = "{\"a\":\"\",\"b\":null,\"c\":[1,2,3,4,5,6],\"d\":{\"x\":\"ok\",\"y\":\" \"}}";

        String compressed = compressor.compress(json, new EntityMemory());

        assertFalse(compressed.contains("\"a\""));
        assertFalse(compressed.contains("\"b\""));
        assertFalse(compressed.contains("\"y\""));
        assertTrue(compressed.contains("\"x\":\"ok\""));
        assertTrue(compressed.contains("...+3 more") || compressed.endsWith("...(truncated)"));
    }

    @Test
    void compress_nonJsonAndTooLong_truncates() {
        String longText = "x".repeat(60);

        String compressed = compressor.compress(longText, new EntityMemory());

        assertTrue(compressed.endsWith("...(truncated)"));
        assertTrue(compressed.length() > 40);
    }
}
