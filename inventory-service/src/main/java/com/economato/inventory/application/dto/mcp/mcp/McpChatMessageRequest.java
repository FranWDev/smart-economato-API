package com.economato.inventory.application.dto.mcp.mcp;

public record McpChatMessageRequest(
        String content,
        String language
) {
}
