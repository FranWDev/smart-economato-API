package com.economato.inventory.application.dto.mcp;

public record McpChatMessageRequest(
        String content,
        String language
) {
}
