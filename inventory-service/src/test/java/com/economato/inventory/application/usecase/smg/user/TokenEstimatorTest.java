package com.economato.inventory.application.usecase.smg.user;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.economato.inventory.application.usecase.smg.model.shared.CompressedMessage;
import com.economato.inventory.domain.model.user.MessageRole;
import com.economato.inventory.infrastructure.config.ai.ai.AiSmgProperties;
import com.knuddels.jtokkit.api.Encoding;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenEstimatorTest {

    @Mock
    private Encoding failingEncoding;

    private AiSmgProperties properties;
    private TokenEstimator tokenEstimator;
    private TokenEstimator fallbackEstimator;

    @BeforeEach
    void setUp() {
        properties = new AiSmgProperties();
        properties.setTokenEstimationDivisor(4);
        tokenEstimator = new TokenEstimator(properties);
        fallbackEstimator = new TokenEstimator(properties, failingEncoding);
        when(failingEncoding.countTokens(anyString())).thenThrow(new RuntimeException("boom"));
    }

    @Test
    void estimate_nullOrBlank_returnsZero() {
        assertEquals(0, tokenEstimator.estimate(null));
        assertEquals(0, tokenEstimator.estimate("   "));
    }

    @Test
    void estimate_nonBlankText_usesTokenizer() {
        assertTrue(tokenEstimator.estimate("hello world") > 0);
    }

    @Test
    void estimate_fallbackUsesNaiveHeuristic() {
        assertEquals(2, fallbackEstimator.estimate("12345678"));
    }

    @Test
    void estimateMessages_sumsMessageContentTokens() {
        List<CompressedMessage> messages = new ArrayList<>();
        messages.add(new CompressedMessage(MessageRole.USER, "12345678", null));
        messages.add(null);
        messages.add(new CompressedMessage(MessageRole.ASSISTANT, "abcd", null));

        assertEquals(fallbackEstimator.estimate("12345678") + fallbackEstimator.estimate("abcd"),
                fallbackEstimator.estimateMessages(messages));
    }
}
