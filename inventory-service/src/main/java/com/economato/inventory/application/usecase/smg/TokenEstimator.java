package com.economato.inventory.application.usecase.smg;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.economato.inventory.application.usecase.smg.model.CompressedMessage;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

@Component
public class TokenEstimator {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding DEFAULT_ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    private final AiSmgProperties aiSmgProperties;
    private final Encoding encoding;

    @Autowired
    public TokenEstimator(AiSmgProperties aiSmgProperties) {
        this(aiSmgProperties, DEFAULT_ENCODING);
    }

    TokenEstimator(AiSmgProperties aiSmgProperties, Encoding encoding) {
        this.aiSmgProperties = aiSmgProperties;
        this.encoding = encoding == null ? DEFAULT_ENCODING : encoding;
    }

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return encoding.countTokens(text);
        } catch (Exception ex) {
            return Math.max(1, text.length() / aiSmgProperties.getTokenEstimationDivisor());
        }
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
