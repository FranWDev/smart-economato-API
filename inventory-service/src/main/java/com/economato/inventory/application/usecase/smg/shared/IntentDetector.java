package com.economato.inventory.application.usecase.smg.shared;

import com.economato.inventory.application.usecase.smg.model.user.UserIntent;
import com.economato.inventory.domain.model.ai.AiChatMessage;
import com.economato.inventory.domain.model.user.MessageRole;
import com.economato.inventory.infrastructure.config.ai.ai.AiIntentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class IntentDetector {

    private static final Map<String, Set<String>> INTENT_TO_TOOLS = Map.of(
            "ORDER_CREATE", Set.of("create-order"),
            "STOCK_CHECK", Set.of("search", "products", "context"),
            "RECIPE_QUERY", Set.of("recipes", "feasibility", "deep"),
            "MENU_PLAN", Set.of("plan-slot", "weekly-plan"),
            "ALLERGEN_CHECK", Set.of("allergens", "allergen-exclusion"),
            "EXPIRY_CHECK", Set.of("expiring-soon", "batches"),
            "CRISIS_MGMT", Set.of("quarantine", "crisis"),
            "COST_ANALYSIS", Set.of("cost-breakdown"),
            "TRACEABILITY", Set.of("ledger", "traceability")
    );

    private final AiIntentProperties aiIntentProperties;

    public List<UserIntent> detect(List<AiChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        Map<String, LocalDateTime> latestIntentAt = new LinkedHashMap<>();
        for (AiChatMessage message : history) {
            if (message.getRole() != MessageRole.USER) {
                continue;
            }
            String normalized = normalize(message.getContent());
            if (normalized.isBlank()) {
                continue;
            }

            for (Map.Entry<String, List<String>> entry : aiIntentProperties.getPatterns().entrySet()) {
                if (containsAny(normalized, entry.getValue())) {
                    latestIntentAt.put(entry.getKey(), message.getCreatedAt());
                }
            }
        }

        if (latestIntentAt.isEmpty()) {
            return List.of();
        }

        List<UserIntent> result = new ArrayList<>();
        for (Map.Entry<String, LocalDateTime> entry : latestIntentAt.entrySet()) {
            boolean resolved = isResolved(entry.getKey(), entry.getValue(), history);
            result.add(new UserIntent(entry.getKey(), entry.getValue(), resolved));
        }
        return result;
    }

    private boolean isResolved(String intent, LocalDateTime detectedAt, List<AiChatMessage> history) {
        Set<String> expectedTools = INTENT_TO_TOOLS.get(intent);
        if (expectedTools == null || expectedTools.isEmpty()) {
            return false;
        }

        for (AiChatMessage message : history) {
            if (message.getRole() != MessageRole.TOOL || message.getToolName() == null) {
                continue;
            }
            if (detectedAt != null && message.getCreatedAt() != null && message.getCreatedAt().isBefore(detectedAt)) {
                continue;
            }
            String tool = normalize(message.getToolName());
            for (String expected : expectedTools) {
                if (tool.contains(normalize(expected))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String text, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (text.contains(normalize(pattern))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        return normalized.trim();
    }
}
