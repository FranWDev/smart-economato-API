package com.economato.inventory.infrastructure.config.ai;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRateLimitPropertiesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @Test
    void messagesPerMinute_belowMinimum_validationFails() {
        AiRateLimitProperties properties = new AiRateLimitProperties();
        properties.setMessagesPerMinute(0);

        Set<ConstraintViolation<AiRateLimitProperties>> violations = validator.validate(properties);

        assertTrue(violations.stream().anyMatch(v -> "messagesPerMinute".equals(v.getPropertyPath().toString())));
    }

    @Test
    void failOpen_defaultTrue() {
        AiRateLimitProperties properties = new AiRateLimitProperties();

        assertTrue(properties.getFailOpen());
    }

    @Test
    void defaults_areCorrect() {
        AiRateLimitProperties properties = new AiRateLimitProperties();

        assertEquals(10, properties.getMessagesPerMinute());
        assertEquals(50, properties.getMaxChatsPerUser());
        assertEquals(500, properties.getMaxMessagesPerChat());
        assertEquals(5, properties.getMaxApiKeysPerUser());
    }
}