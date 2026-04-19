package com.economato.inventory.infrastructure.config.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class CollaborationI18nKeysTest {

    @Test
    void collaborationKeysExistInDefaultSpanishAndEnglishBundles() throws Exception {
        assertBundleContainsKeys("i18n/messages.properties");
        assertBundleContainsKeys("i18n/messages_es.properties");
        assertBundleContainsKeys("i18n/messages_en.properties");
    }

    private void assertBundleContainsKeys(String classpathFile) throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(classpathFile)) {
            assertTrue(inputStream != null, "Bundle not found: " + classpathFile);
            properties.load(inputStream);
        }

        assertTrue(properties.containsKey(MessageKey.ERROR_ORDER_COLLAB_NOT_ACTIVE.getKey()),
                "Missing key in " + classpathFile + ": " + MessageKey.ERROR_ORDER_COLLAB_NOT_ACTIVE.getKey());
        assertTrue(properties.containsKey(MessageKey.ERROR_ORDER_COLLAB_NO_PERMISSION_ADMIT.getKey()),
                "Missing key in " + classpathFile + ": " + MessageKey.ERROR_ORDER_COLLAB_NO_PERMISSION_ADMIT.getKey());
        assertTrue(properties.containsKey(MessageKey.ERROR_ORDER_COLLAB_REQUEST_NOT_FOUND.getKey()),
                "Missing key in " + classpathFile + ": " + MessageKey.ERROR_ORDER_COLLAB_REQUEST_NOT_FOUND.getKey());
        assertTrue(properties.containsKey(MessageKey.ERROR_ORDER_COLLAB_FIELD_PATH_REQUIRED.getKey()),
                "Missing key in " + classpathFile + ": " + MessageKey.ERROR_ORDER_COLLAB_FIELD_PATH_REQUIRED.getKey());
        assertTrue(properties.containsKey(MessageKey.ERROR_ORDER_COLLAB_FIELD_LOCKED.getKey()),
                "Missing key in " + classpathFile + ": " + MessageKey.ERROR_ORDER_COLLAB_FIELD_LOCKED.getKey());
    }
}
