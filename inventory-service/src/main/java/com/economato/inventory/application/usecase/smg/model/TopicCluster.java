package com.economato.inventory.application.usecase.smg.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class TopicCluster {

    private int index;
    private int startIdx;
    private int endIdx;
    private Set<String> entityNames = new HashSet<>();
    private Set<String> toolsUsed = new HashSet<>();
    private Set<String> intentsDetected = new HashSet<>();
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String lastAssistantSnippet;

    public String fullSummary() {
        if (toolsUsed.contains("cook-recipe")) {
            return "Cooked recipes: " + safeEntityNames() + ". Stock adjusted.";
        }
        if (toolsUsed.contains("create-order")) {
            return "Order flow executed with entities: " + safeEntityNames() + ".";
        }
        if (toolsUsed.contains("search")) {
            return "Search topic: " + safeEntityNames() + ".";
        }
        if (toolsUsed.contains("feasibility")) {
            return "Feasibility checked for: " + safeEntityNames() + ".";
        }
        if (toolsUsed.contains("allergen")) {
            return "Allergen checks executed for: " + safeEntityNames() + ".";
        }
        return "Topic around: " + safeEntityNames() + ".";
    }

    public String oneLineSummary() {
        return "[T" + index + "|" + startIdx + "-" + endIdx + "] " +
                String.join("|", toolsUsed) + ": " + safeEntityNames();
    }

    public String minimalSummary() {
        String primaryIntent = intentsDetected.stream().findFirst().orElse("GENERAL");
        return "[T" + index + "] " + primaryIntent;
    }

    private String safeEntityNames() {
        if (entityNames.isEmpty()) {
            return "general context";
        }
        return String.join(", ", entityNames);
    }
}
