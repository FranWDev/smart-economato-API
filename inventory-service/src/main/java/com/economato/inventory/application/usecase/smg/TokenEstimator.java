package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.usecase.smg.model.CompressedMessage;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TokenEstimator {

    private final AiSmgProperties aiSmgProperties;

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / aiSmgProperties.getTokenEstimationDivisor());
    }

    public int estimateMessages(List<CompressedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream()
                .mapToInt(m -> estimate(m == null ? null : m.content()))
                .sum();
    }
}
