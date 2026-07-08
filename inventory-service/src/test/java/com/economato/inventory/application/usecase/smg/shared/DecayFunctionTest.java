package com.economato.inventory.application.usecase.smg.shared;

import com.economato.inventory.application.usecase.smg.model.shared.TopicCluster;
import com.economato.inventory.infrastructure.config.ai.ai.AiSmgProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecayFunctionTest {

    private DecayFunction decayFunction;

    @BeforeEach
    void setUp() {
        AiSmgProperties properties = new AiSmgProperties();
        properties.setDecayLambda(3.0);
        properties.setDecayFullThreshold(0.7);
        properties.setDecayOnelinerThreshold(0.3);
        decayFunction = new DecayFunction(properties);
    }

    @Test
    void apply_invalidInput_returnsGeneralFallback() {
        assertEquals("[T?] GENERAL", decayFunction.apply(null, 10));
        assertEquals("[T?] GENERAL", decayFunction.apply(topic(1, 1), 0));
    }

    @Test
    void apply_recentTopic_returnsFullSummary() {
        TopicCluster topic = topic(1, 10);
        String summary = decayFunction.apply(topic, 10);

        assertTrue(summary.contains("Search topic:"));
    }

    @Test
    void apply_midAgeTopic_returnsOneLineSummary() {
        TopicCluster topic = topic(2, 7);

        assertEquals("[T2|1-7] search: tomate", decayFunction.apply(topic, 10));
    }

    @Test
    void apply_oldTopic_returnsMinimalSummary() {
        TopicCluster topic = topic(3, 0);

        assertEquals("[T3] STOCK_CHECK", decayFunction.apply(topic, 10));
    }

    private TopicCluster topic(int index, int endIdx) {
        TopicCluster topic = new TopicCluster();
        topic.setIndex(index);
        topic.setStartIdx(1);
        topic.setEndIdx(endIdx);
        topic.getEntityNames().add("tomate");
        topic.getToolsUsed().add("search");
        topic.getIntentsDetected().add("STOCK_CHECK");
        return topic;
    }
}
