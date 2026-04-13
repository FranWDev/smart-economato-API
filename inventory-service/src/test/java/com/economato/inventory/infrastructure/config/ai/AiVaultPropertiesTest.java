package com.economato.inventory.infrastructure.config.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AiVaultPropertiesTest {

    private AiVaultProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiVaultProperties();
        properties.setMasterKey("master-key");
        properties.setCurrentKeyVersion(1);
    }

    @Test
    void getKeyForVersion_withEmptyVersionMap_returnsMasterKey() {
        properties.setKeyVersions(Map.of());

        String key = properties.getKeyForVersion(1);

        assertEquals("master-key", key);
    }

    @Test
    void getKeyForVersion_withPopulatedMap_returnsCorrectKey() {
        properties.setKeyVersions(Map.of(1, "key-v1", 2, "key-v2"));

        String key = properties.getKeyForVersion(2);

        assertEquals("key-v2", key);
    }

    @Test
    void getKeyForVersion_withNonExistentVersion_throwsIllegalArgument() {
        properties.setKeyVersions(Map.of(1, "key-v1"));

        assertThrows(IllegalArgumentException.class, () -> properties.getKeyForVersion(3));
    }

    @Test
    void getKeyForVersion_withCurrentVersion_returnsCurrentKey() {
        properties.setCurrentKeyVersion(2);
        properties.setKeyVersions(Map.of(1, "key-v1", 2, "key-v2"));

        String key = properties.getKeyForVersion(properties.getCurrentKeyVersion());

        assertEquals("key-v2", key);
    }
}
