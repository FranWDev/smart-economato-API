package com.economato.inventory.application.usecase.smg.model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.economato.inventory.domain.model.MessageRole;

class CompressedContextTest {

    @Test
    void toPromptString_formatsStructuredSections() {
        CompressedContext context = new CompressedContext(
                "date: 2026-04-20, language: es, total_products: 7, pending_orders: 2, total_recipes: 3, active_alerts: 1",
                "[active] ORDER_CREATE\n[resolved] -",
                "## Products\n- Harina (ID:42): 5 KG, price: 2.50\n",
                "topic-one\n",
                List.of(
                        new CompressedMessage(MessageRole.USER, "hola", null),
                        new CompressedMessage(MessageRole.ASSISTANT, "respuesta", "search")
                ),
                123,
                0.75,
                "es"
        );

        String prompt = context.toPromptString();

        assertTrue(prompt.contains("# System State"));
        assertTrue(prompt.contains("# Detected Intent"));
        assertTrue(prompt.contains("# Referenced Data"));
        assertTrue(prompt.contains("# Previous Topics"));
        assertTrue(prompt.contains("# Conversation"));
        assertTrue(prompt.contains("[USER] hola"));
        assertTrue(prompt.contains("[ASSISTANT] respuesta (tool: search)"));
        assertTrue(prompt.contains("tokens_used=123 | compression=0.75 | lang=es"));
    }

    @Test
    void toPromptString_skipsSentinelSectionsAndShowsEmptyConversation() {
        CompressedContext context = new CompressedContext(
                "state",
                "no-intent",
                "no-entities",
                "no-topics",
                List.of(),
                10,
                1.0,
                null
        );

        String prompt = context.toPromptString();

        assertTrue(prompt.contains("# System State"));
        assertTrue(prompt.contains("(no previous messages)"));
        assertTrue(prompt.contains("tokens_used=10 | compression=1.0 | lang=-"));
        assertFalse(prompt.contains("# Detected Intent"));
        assertFalse(prompt.contains("# Referenced Data"));
        assertFalse(prompt.contains("# Previous Topics"));
    }
}
