package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.usecase.smg.model.UserIntent;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.infrastructure.config.ai.AiIntentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentDetectorTest {

    private IntentDetector intentDetector;

    @BeforeEach
    void setUp() {
        intentDetector = new IntentDetector(new AiIntentProperties());
    }

    @Test
    void detect_identifiesResolvedAndUnresolvedIntents() {
        LocalDateTime now = LocalDateTime.now();
        List<AiChatMessage> history = List.of(
                user("Necesitamos comprar tomate", now.minusMinutes(3)),
                tool("create-order-v2", now.minusMinutes(2)),
                assistant("puedo ayudarte", now.minusMinutes(1)),
                user("revisa stock de queso", now)
        );

        Map<String, UserIntent> byIntent = intentDetector.detect(history)
                .stream()
                .collect(Collectors.toMap(UserIntent::intentType, Function.identity()));

        assertTrue(byIntent.containsKey("ORDER_CREATE"));
        assertTrue(byIntent.get("ORDER_CREATE").resolved());
        assertTrue(byIntent.containsKey("STOCK_CHECK"));
        assertFalse(byIntent.get("STOCK_CHECK").resolved());
    }

    @Test
    void detect_normalizesAccentsAndCase() {
        List<AiChatMessage> history = List.of(
                user("Necesito validar alergeno en menu", LocalDateTime.now())
        );

        List<UserIntent> intents = intentDetector.detect(history);

        assertTrue(intents.stream().anyMatch(i -> i.intentType().equals("ALLERGEN_CHECK")));
    }

    private AiChatMessage user(String content, LocalDateTime createdAt) {
        return AiChatMessage.builder()
                .role(MessageRole.USER)
                .content(content)
                .createdAt(createdAt)
                .build();
    }

    private AiChatMessage assistant(String content, LocalDateTime createdAt) {
        return AiChatMessage.builder()
                .role(MessageRole.ASSISTANT)
                .content(content)
                .createdAt(createdAt)
                .build();
    }

    private AiChatMessage tool(String toolName, LocalDateTime createdAt) {
        return AiChatMessage.builder()
                .role(MessageRole.TOOL)
                .toolName(toolName)
                .createdAt(createdAt)
                .build();
    }
}
