package com.economato.inventory.application.dto.mcp;

public record NestStreamEvent(
        String type,
        String data,
        String fullResponse,
        Integer inputTokens,
        Integer outputTokens
) {
}
