package com.economato.inventory.application.usecase.smg.shared;
import com.economato.inventory.domain.model.order.Order;

import com.economato.inventory.application.usecase.smg.model.shared.TopicCluster;
import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.user.MessageRole;
import com.economato.inventory.infrastructure.config.ai.ai.AiSmgProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicClustererTest {

    private TopicClusterer topicClusterer;

    @BeforeEach
    void setUp() {
        AiSmgProperties properties = new AiSmgProperties();
        properties.setTopicSplitGapMinutes(5);
        properties.setTopicEntityChangeThreshold(1.0);
        topicClusterer = new TopicClusterer(properties);
    }

    @Test
    void cluster_splitsByTimeGapAndTracksClusterMetadata() {
        LocalDateTime base = LocalDateTime.of(2026, 4, 13, 10, 0);
        List<AiChatMessage> history = List.of(
                message(MessageRole.USER, "Necesitamos stock de tomate", null, base),
                message(MessageRole.TOOL, null, "search", base.plusMinutes(1)),
                message(MessageRole.ASSISTANT, "Encontré tomate en inventario.", null, base.plusMinutes(2)),
                message(MessageRole.USER, "Queremos comprar aceite", null, base.plusMinutes(12)),
                message(MessageRole.TOOL, null, "create-order", base.plusMinutes(13)),
                message(MessageRole.ASSISTANT, repeat('x', 120), null, base.plusMinutes(14))
        );

        List<TopicCluster> clusters = topicClusterer.cluster(history);

        assertEquals(2, clusters.size());

        TopicCluster first = clusters.get(0);
        assertEquals(1, first.getIndex());
        assertEquals(0, first.getStartIdx());
        assertEquals(2, first.getEndIdx());
        assertTrue(first.getToolsUsed().contains("search"));
        assertTrue(first.getIntentsDetected().contains("STOCK_CHECK"));

        TopicCluster second = clusters.get(1);
        assertEquals(2, second.getIndex());
        assertEquals(3, second.getStartIdx());
        assertEquals(5, second.getEndIdx());
        assertTrue(second.getToolsUsed().contains("create-order"));
        assertEquals(100, second.getLastAssistantSnippet().length());
    }

    @Test
    void topicSummaries_reflectToolsAndIntent() {
        TopicCluster cluster = new TopicCluster();
        cluster.setIndex(3);
        cluster.setStartIdx(1);
        cluster.setEndIdx(4);
        cluster.getEntityNames().add("tomate");
        cluster.getToolsUsed().add("create-order");
        cluster.getIntentsDetected().add("ORDER_CREATE");

        assertEquals("Order flow executed with entities: tomate.", cluster.fullSummary());
        assertEquals("[T3|1-4] create-order: tomate", cluster.oneLineSummary());
        assertEquals("[T3] ORDER_CREATE", cluster.minimalSummary());
    }

    private AiChatMessage message(MessageRole role, String content, String toolName, LocalDateTime createdAt) {
        return AiChatMessage.builder()
                .role(role)
                .content(content)
                .toolName(toolName)
                .createdAt(createdAt)
                .build();
    }

    private String repeat(char character, int count) {
        return String.valueOf(character).repeat(count);
    }
}