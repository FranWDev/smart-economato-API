package com.economato.inventory.application.usecase.smg;

import com.economato.inventory.application.usecase.mcp.McpUtilityService;
import com.economato.inventory.application.usecase.smg.model.CompressedContext;
import com.economato.inventory.application.usecase.smg.model.CompressedMessage;
import com.economato.inventory.application.usecase.smg.model.EntityMemory;
import com.economato.inventory.application.usecase.smg.model.TopicCluster;
import com.economato.inventory.application.usecase.smg.model.UserIntent;
import com.economato.inventory.domain.model.AiChatMessage;
import com.economato.inventory.domain.model.MessageRole;
import com.economato.inventory.infrastructure.config.ai.AiSmgProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SemanticMemoryGraphService {

    private final TokenEstimator tokenEstimator;
    private final EntityExtractor entityExtractor;
    private final EntityEnricher entityEnricher;
    private final TopicClusterer topicClusterer;
    private final IntentDetector intentDetector;
    private final DecayFunction decayFunction;
    private final ToolResultCompressor toolResultCompressor;
    private final AiSmgProperties aiSmgProperties;
    private final McpUtilityService mcpUtilityService;
    private final MeterRegistry meterRegistry;

    public CompressedContext compress(List<AiChatMessage> history, String userLanguage) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<AiChatMessage> messages = history == null ? List.of() : history;
            if (messages.isEmpty()) {
                String system = buildSystemContext(userLanguage);
                return new CompressedContext(system, "-", "-", "-", List.of(), tokenEstimator.estimate(system), 1d, userLanguage);
            }

            int tokenBudget = aiSmgProperties.getTokenBudget();
            int wmBudget = (int) (tokenBudget * aiSmgProperties.getWorkingMemoryWeight());
            int entityBudget = (int) (tokenBudget * aiSmgProperties.getEntityMemoryWeight());
            int topicBudget = (int) (tokenBudget * aiSmgProperties.getTopicMemoryWeight());
            int intentBudget = (int) (tokenBudget * aiSmgProperties.getIntentMemoryWeight());
            int systemBudget = (int) (tokenBudget * aiSmgProperties.getSystemContextWeight());

            EntityMemory entityMemory = entityExtractor.extract(messages);
            entityEnricher.enrich(entityMemory);

            List<TopicCluster> topics = topicClusterer.cluster(messages);
            List<UserIntent> intents = intentDetector.detect(messages);

            String systemContext = truncateToBudget(buildSystemContext(userLanguage), systemBudget);
            String intentMemory = truncateToBudget(serializeIntents(intents), intentBudget);
            String entityMemoryText = truncateToBudget(entityMemory.serialize(), entityBudget);
            String topicMemory = truncateToBudget(serializeTopics(topics, messages.size()), topicBudget);

            List<CompressedMessage> workingMemory = buildWorkingMemory(messages, wmBudget, entityMemory);

            int compressedTokens = tokenEstimator.estimate(systemContext)
                    + tokenEstimator.estimate(intentMemory)
                    + tokenEstimator.estimate(entityMemoryText)
                    + tokenEstimator.estimate(topicMemory)
                    + tokenEstimator.estimateMessages(workingMemory);

            int originalTokens = estimateOriginalTokens(messages);
            double ratio = compressedTokens == 0 ? 1d : (double) originalTokens / (double) compressedTokens;

            meterRegistry.counter("ai.smg.entities.extracted").increment(entityMemory.totalEntityCount());
            meterRegistry.gauge("ai.smg.compression.ratio", ratio);

            return new CompressedContext(
                    systemContext,
                    intentMemory,
                    entityMemoryText,
                    topicMemory,
                    workingMemory,
                    compressedTokens,
                    ratio,
                    userLanguage
            );
        } finally {
            sample.stop(meterRegistry.timer("ai.smg.compression.duration"));
        }
    }

    private List<CompressedMessage> buildWorkingMemory(List<AiChatMessage> history, int budget, EntityMemory entityMemory) {
        if (history.isEmpty() || budget <= 0) {
            return List.of();
        }

        List<AiChatMessage> reversed = new ArrayList<>(history);
        reversed.sort(Comparator.comparing(AiChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        List<CompressedMessage> collected = new ArrayList<>();
        int used = 0;
        for (AiChatMessage message : reversed) {
            if (collected.size() >= aiSmgProperties.getMaxWorkingMemoryMessages()) {
                break;
            }

            String content = message.getContent();
            if (message.getRole() == MessageRole.TOOL) {
                content = toolResultCompressor.compress(message.getToolResult(), entityMemory);
            }
            String safeContent = content == null ? "" : content;

            int estimate = tokenEstimator.estimate(safeContent);
            if (used + estimate > budget) {
                int remaining = budget - used;
                if (remaining <= 0) {
                    break;
                }
                safeContent = smartTruncateByTokens(safeContent, remaining);
                estimate = tokenEstimator.estimate(safeContent);
                if (estimate <= 0) {
                    break;
                }
            }

            collected.add(new CompressedMessage(message.getRole(), safeContent, message.getToolName()));
            used += estimate;
            if (used >= budget) {
                break;
            }
        }

        Collections.reverse(collected);
        return collected;
    }

    private String serializeTopics(List<TopicCluster> topics, int totalMessages) {
        if (topics == null || topics.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (TopicCluster topic : topics) {
            sb.append(decayFunction.apply(topic, totalMessages)).append("\n");
        }
        return sb.toString().trim();
    }

    private String serializeIntents(List<UserIntent> intents) {
        if (intents == null || intents.isEmpty()) {
            return "-";
        }

        List<String> active = intents.stream()
                .filter(intent -> !intent.resolved())
                .map(UserIntent::intentType)
                .toList();
        List<String> resolved = intents.stream()
                .filter(UserIntent::resolved)
                .map(UserIntent::intentType)
                .toList();

        String activeText = active.isEmpty() ? "-" : String.join(", ", active);
        String resolvedText = resolved.isEmpty() ? "-" : String.join(", ", resolved);
        return "[active] " + activeText + "\n[resolved] " + resolvedText;
    }

    private int estimateOriginalTokens(List<AiChatMessage> messages) {
        int sum = 0;
        for (AiChatMessage message : messages) {
            sum += tokenEstimator.estimate(message.getContent());
            sum += tokenEstimator.estimate(message.getToolResult());
        }
        return sum;
    }

    private String buildSystemContext(String userLanguage) {
        return "date=" + LocalDate.now() +
                ", language=" + (userLanguage == null || userLanguage.isBlank() ? "es" : userLanguage) +
                ", context=" + mcpUtilityService.getSystemContext();
    }

    private String truncateToBudget(String text, int budgetTokens) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        int estimate = tokenEstimator.estimate(text);
        if (estimate <= budgetTokens) {
            return text;
        }
        return smartTruncateByTokens(text, budgetTokens);
    }

    private String smartTruncateByTokens(String text, int budgetTokens) {
        if (text == null || text.isBlank() || budgetTokens <= 0) {
            return "";
        }
        int maxChars = Math.max(1, budgetTokens * aiSmgProperties.getTokenEstimationDivisor());
        if (text.length() <= maxChars) {
            return text;
        }

        String candidate = text.substring(0, maxChars);
        int dotIdx = candidate.lastIndexOf('.');
        int lineIdx = candidate.lastIndexOf('\n');
        int cut = Math.max(dotIdx, lineIdx);
        if (cut > 0) {
            candidate = candidate.substring(0, cut + 1);
        }
        return candidate + " ...(truncated)";
    }
}
