package com.economato.inventory.infrastructure.config.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AiSmgPropertiesTest {

    private AiSmgProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AiSmgProperties();
        properties.setTokenBudget(6000);
        properties.setWorkingMemoryWeight(0.57);
        properties.setEntityMemoryWeight(0.20);
        properties.setTopicMemoryWeight(0.10);
        properties.setIntentMemoryWeight(0.03);
        properties.setSystemContextWeight(0.10);
    }

    @Test
    void weightsSumToOne_withinTolerance() {
        double sum = properties.getWorkingMemoryWeight()
                + properties.getEntityMemoryWeight()
                + properties.getTopicMemoryWeight()
                + properties.getIntentMemoryWeight()
                + properties.getSystemContextWeight();

        assertTrue(Math.abs(1.0 - sum) <= 0.01);
    }

    @Test
    void tokenBudgetDistribution_respectsWeights() {
        int tokenBudget = properties.getTokenBudget();

        int wm = (int) (tokenBudget * properties.getWorkingMemoryWeight());
        int entity = (int) (tokenBudget * properties.getEntityMemoryWeight());
        int topic = (int) (tokenBudget * properties.getTopicMemoryWeight());
        int intent = (int) (tokenBudget * properties.getIntentMemoryWeight());
        int system = (int) (tokenBudget * properties.getSystemContextWeight());

        assertTrue(wm > entity);
        assertTrue(entity > topic);
        assertTrue(topic > intent);
        assertEquals(600, system);
    }
}
