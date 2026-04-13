package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.usecase.smg.model.CompressedMessage;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenEstimatorTest {

    private TokenEstimator tokenEstimator;

    @BeforeEach
    void setUp() {
        AiSmgProperties properties = new AiSmgProperties();
        properties.setTokenEstimationDivisor(4);
        tokenEstimator = new TokenEstimator(properties);
    }

    @Test
    void estimate_nullOrBlank_returnsZero() {
        assertEquals(0, tokenEstimator.estimate(null));
        assertEquals(0, tokenEstimator.estimate("   "));
    }

    @Test
    void estimate_shortText_returnsAtLeastOne() {
        assertEquals(1, tokenEstimator.estimate("abc"));
    }

    @Test
    void estimateMessages_sumsMessageContentTokens() {
        List<CompressedMessage> messages = new ArrayList<>();
        messages.add(new CompressedMessage(MessageRole.USER, "12345678", null));
        messages.add(null);
        messages.add(new CompressedMessage(MessageRole.ASSISTANT, "abcd", null));

        assertEquals(3, tokenEstimator.estimateMessages(messages));
    }
}
