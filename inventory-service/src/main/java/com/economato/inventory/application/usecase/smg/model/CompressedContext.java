package com.economato.inventory.application.usecase.smg.model;

import java.util.List;

public record CompressedContext(
        String systemContext,
        String intentMemory,
        String entityMemory,
        String topicMemory,
        List<CompressedMessage> workingMemory,
        int totalEstimatedTokens,
        double compressionRatio,
        String userLanguage
) {
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SYSTEM ===\n").append(defaultText(systemContext())).append("\n\n");
        sb.append("=== INTENTS ===\n").append(defaultText(intentMemory())).append("\n\n");
        sb.append("=== ENTITIES ===\n").append(defaultText(entityMemory())).append("\n\n");
        sb.append("=== TOPICS ===\n").append(defaultText(topicMemory())).append("\n\n");
        sb.append("=== CONVERSATION ===\n");

        if (workingMemory() == null || workingMemory().isEmpty()) {
            sb.append("no-recent-messages\n");
        } else {
            for (CompressedMessage message : workingMemory()) {
                sb.append("[")
                        .append(message.role() == null ? "UNKNOWN" : message.role().name())
                        .append("] ")
                        .append(defaultText(message.content()));
                if (message.toolName() != null && !message.toolName().isBlank()) {
                    sb.append(" {tool=").append(message.toolName()).append("}");
                }
                sb.append("\n");
            }
        }

        sb.append("\nmeta: tokens=").append(totalEstimatedTokens())
                .append(", ratio=").append(compressionRatio())
                .append(", lang=").append(defaultText(userLanguage()));

        return sb.toString();
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
