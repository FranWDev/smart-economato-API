package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.usecase.smg.model.TopicCluster;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TopicClusterer {

    private final AiSmgProperties aiSmgProperties;

    public List<TopicCluster> cluster(List<AiChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        List<TopicCluster> clusters = new ArrayList<>();
        TopicCluster current = null;

        for (int i = 0; i < history.size(); i++) {
            AiChatMessage message = history.get(i);
            if (current == null || shouldStartNewCluster(current, message, i)) {
                current = new TopicCluster();
                current.setIndex(clusters.size() + 1);
                current.setStartIdx(i);
                current.setStartTime(message.getCreatedAt());
                clusters.add(current);
            }

            current.setEndIdx(i);
            current.setEndTime(message.getCreatedAt());
            updateCluster(current, message);
        }

        return clusters;
    }

    private boolean shouldStartNewCluster(TopicCluster current, AiChatMessage message, int currentIdx) {
        if (current.getEndTime() != null && message.getCreatedAt() != null) {
            long minutes = Duration.between(current.getEndTime(), message.getCreatedAt()).toMinutes();
            if (minutes > aiSmgProperties.getTopicSplitGapMinutes()) {
                return true;
            }
        }

        Set<String> currentEntities = current.getEntityNames();
        Set<String> incomingEntities = extractEntityHints(message);
        if (!currentEntities.isEmpty() && !incomingEntities.isEmpty()) {
            double distance = jaccardDistance(currentEntities, incomingEntities);
            if (distance > aiSmgProperties.getTopicEntityChangeThreshold()) {
                return true;
            }
        }

        return false;
    }

    private void updateCluster(TopicCluster cluster, AiChatMessage message) {
        if (message.getToolName() != null && !message.getToolName().isBlank()) {
            cluster.getToolsUsed().add(message.getToolName().toLowerCase());
        }
        cluster.getEntityNames().addAll(extractEntityHints(message));
        if (message.getRole() == MessageRole.ASSISTANT && message.getContent() != null) {
            String snippet = message.getContent().length() > 100
                    ? message.getContent().substring(0, 100)
                    : message.getContent();
            cluster.setLastAssistantSnippet(snippet);
        }
        if (message.getRole() == MessageRole.USER) {
            String userIntent = inferIntent(message.getContent());
            if (userIntent != null) {
                cluster.getIntentsDetected().add(userIntent);
            }
        }
    }

    private Set<String> extractEntityHints(AiChatMessage message) {
        Set<String> hints = new HashSet<>();
        if (message.getToolName() != null) {
            hints.add(message.getToolName().toLowerCase());
        }
        if (message.getContent() != null && !message.getContent().isBlank()) {
            String content = message.getContent().toLowerCase();
            for (String token : content.split("[^a-z0-9]+")) {
                if (token.length() >= 5) {
                    hints.add(token);
                }
            }
        }
        return hints;
    }

    private String inferIntent(String content) {
        if (content == null) {
            return null;
        }
        String text = content.toLowerCase();
        if (text.contains("stock") || text.contains("inventario")) {
            return "STOCK_CHECK";
        }
        if (text.contains("pedido") || text.contains("comprar")) {
            return "ORDER_CREATE";
        }
        if (text.contains("menu") || text.contains("semana")) {
            return "MENU_PLAN";
        }
        if (text.contains("receta") || text.contains("ingrediente")) {
            return "RECIPE_QUERY";
        }
        return null;
    }

    private double jaccardDistance(Set<String> left, Set<String> right) {
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        if (union.isEmpty()) {
            return 0d;
        }

        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return 1d - ((double) intersection.size() / (double) union.size());
    }
}
