package com.economato.inventory.infrastructure.config.ai.ai;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiNestPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @Test
    void baseUrl_notBlank_validationFails() {
        AiNestProperties properties = validProperties();
        properties.setBaseUrl(" ");

        Set<ConstraintViolation<AiNestProperties>> violations = validator.validate(properties);

        assertTrue(violations.stream().anyMatch(v -> "baseUrl".equals(v.getPropertyPath().toString())));
    }

    @Test
    void serviceKey_notBlank_validationFails() {
        AiNestProperties properties = validProperties();
        properties.setServiceKey("");

        Set<ConstraintViolation<AiNestProperties>> violations = validator.validate(properties);

        assertTrue(violations.stream().anyMatch(v -> "serviceKey".equals(v.getPropertyPath().toString())));
    }

    @Test
    void streamTimeoutMs_belowMinimum_validationFails() {
        AiNestProperties properties = validProperties();
        properties.setStreamTimeoutMs(4000L);

        Set<ConstraintViolation<AiNestProperties>> violations = validator.validate(properties);

        assertTrue(violations.stream().anyMatch(v -> "streamTimeoutMs".equals(v.getPropertyPath().toString())));
    }

    @Test
    void defaults_areCorrect() {
        AiNestProperties properties = new AiNestProperties();

        assertEquals(120000L, properties.getStreamTimeoutMs());
        assertEquals(5000L, properties.getConnectionTimeoutMs());
        assertEquals(60000L, properties.getReadTimeoutMs());
        assertEquals(2, properties.getMaxRetries());
        assertEquals("/api/completion", properties.getCompletionEndpoint());
    }

    private AiNestProperties validProperties() {
        AiNestProperties properties = new AiNestProperties();
        properties.setBaseUrl("http://localhost:3000");
        properties.setServiceKey("service-key");
        properties.setAllowedOrigin("http://localhost:3000");
        properties.setStreamTimeoutMs(120000L);
        properties.setConnectionTimeoutMs(5000L);
        properties.setReadTimeoutMs(60000L);
        properties.setMaxRetries(2);
        return properties;
    }
}