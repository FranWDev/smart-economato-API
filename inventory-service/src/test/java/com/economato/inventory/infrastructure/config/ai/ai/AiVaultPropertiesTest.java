package com.economato.inventory.infrastructure.config.ai.ai;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class AiVaultPropertiesTest {

    private AiVaultProperties properties;
    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        properties = new AiVaultProperties();
        properties.setMasterKey("master-key");
        properties.setCurrentKeyVersion(1);
    }

    @Test
    void masterKey_notBlank_validationFails() {
        properties.setMasterKey(" ");

        Set<ConstraintViolation<AiVaultProperties>> violations = validator.validate(properties);

        assertTrue(violations.stream().anyMatch(v -> "masterKey".equals(v.getPropertyPath().toString())));
    }

    @Test
    void currentKeyVersion_defaultIsOne() {
        AiVaultProperties defaults = new AiVaultProperties();

        assertEquals(1, defaults.getCurrentKeyVersion());
    }

    @Test
    void getKeyForVersion_emptyMap_returnsMasterKey() {
        properties.setKeyVersions(Map.of());

        String key = properties.getKeyForVersion(1);

        assertEquals("master-key", key);
    }

    @Test
    void getKeyForVersion_withMap_returnsCorrectKey() {
        properties.setKeyVersions(Map.of(1, "key-v1", 2, "key-v2"));

        String key = properties.getKeyForVersion(2);

        assertEquals("key-v2", key);
    }

    @Test
    void getKeyForVersion_unknownVersion_throwsException() {
        properties.setKeyVersions(Map.of(1, "key-v1"));

        assertThrows(IllegalArgumentException.class, () -> properties.getKeyForVersion(3));
    }
}
