package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.usecase.smg.model.TopicCluster;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DecayFunction {

    private final AiSmgProperties aiSmgProperties;

    public String apply(TopicCluster topic, int totalMessages) {
        if (topic == null || totalMessages <= 0) {
            return "[T?] GENERAL";
        }

        double age = 1.0d - ((double) topic.getEndIdx() / (double) totalMessages);
        double decay = Math.exp(-aiSmgProperties.getDecayLambda() * age);

        if (decay > aiSmgProperties.getDecayFullThreshold()) {
            return topic.fullSummary();
        }
        if (decay > aiSmgProperties.getDecayOnelinerThreshold()) {
            return topic.oneLineSummary();
        }
        return topic.minimalSummary();
    }
}
