package com.economato.inventory.infrastructure.config.ai;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class AiSmgPropertiesTest {

    private AiSmgProperties properties;
    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
        properties = new AiSmgProperties();
        properties.setTokenBudget(7000);
        properties.setWorkingMemoryWeight(0.60);
        properties.setEntityMemoryWeight(0.22);
        properties.setTopicMemoryWeight(0.08);
        properties.setIntentMemoryWeight(0.03);
        properties.setSystemContextWeight(0.07);
    }

    @Test
    void tokenBudget_belowMinimum_validationFails() {
        properties.setTokenBudget(999);

        Set<ConstraintViolation<AiSmgProperties>> violations = validator.validate(properties);

        assertTrue(violations.stream().anyMatch(v -> "tokenBudget".equals(v.getPropertyPath().toString())));
    }

    @Test
    void layerWeights_sumToOne() {
        double sum = properties.getWorkingMemoryWeight()
                + properties.getEntityMemoryWeight()
                + properties.getTopicMemoryWeight()
                + properties.getIntentMemoryWeight()
                + properties.getSystemContextWeight();

        assertTrue(Math.abs(1.0 - sum) <= 0.01);
    }

    @Test
    void decayLambda_negativeValue_validationFails() {
        properties.setDecayLambda(-1.0);

        Set<ConstraintViolation<AiSmgProperties>> violations = validator.validate(properties);

        assertTrue(violations.stream().anyMatch(v -> "decayLambda".equals(v.getPropertyPath().toString())));
    }

    @Test
    void defaults_areCorrect() {
        AiSmgProperties defaults = new AiSmgProperties();

        assertEquals(7000, defaults.getTokenBudget());
        assertEquals(3.0, defaults.getDecayLambda());
        assertEquals(5, defaults.getTopicSplitGapMinutes());
    }
}
