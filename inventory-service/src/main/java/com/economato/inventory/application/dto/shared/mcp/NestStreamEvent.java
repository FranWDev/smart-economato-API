package com.economato.inventory.application.dto.shared.mcp;

public record NestStreamEvent(
        String type,
        String data,
        String fullResponse,
        String thinkingContent,
        Integer inputTokens,
        Integer outputTokens
) {
}
