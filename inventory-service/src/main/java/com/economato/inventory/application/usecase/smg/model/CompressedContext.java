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

        sb.append("# System State\n").append(defaultText(systemContext())).append("\n\n");

        if (intentMemory() != null && !intentMemory().isBlank() && !"no-intent".equals(intentMemory())) {
            sb.append("# Detected Intent\n").append(defaultText(intentMemory())).append("\n\n");
        }

        if (entityMemory() != null && !entityMemory().isBlank() && !"no-entities".equals(entityMemory())) {
            sb.append("# Referenced Data\n").append(defaultText(entityMemory())).append("\n\n");
        }

        if (topicMemory() != null && !topicMemory().isBlank() && !"no-topics".equals(topicMemory())) {
            sb.append("# Previous Topics\n").append(defaultText(topicMemory())).append("\n\n");
        }

        sb.append("# Conversation\n");

        if (workingMemory() == null || workingMemory().isEmpty()) {
            sb.append("(no previous messages)\n");
        } else {
            for (CompressedMessage message : workingMemory()) {
                String role = message.role() == null ? "UNKNOWN" : message.role().name();
                sb.append("[").append(role).append("] ").append(defaultText(message.content()));
                if (message.toolName() != null && !message.toolName().isBlank()) {
                    sb.append(" (tool: ").append(message.toolName()).append(")");
                }
                sb.append("\n");
            }
        }

        sb.append("\n---\n");
        sb.append("tokens_used=").append(totalEstimatedTokens())
                .append(" | compression=").append(compressionRatio())
                .append(" | lang=").append(defaultText(userLanguage()));

        return sb.toString();
    }

    private String defaultText(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
