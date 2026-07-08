package com.economato.inventory.application.dto.mcp.mcp;

import java.time.LocalDateTime;

public record McpChatMessageResponseDto(
        Long id,
        String role,
        String content,
        String toolName,
        String toolCallId,
        String toolResult,
        String thinkingContent,
        int inputTokens,
        int outputTokens,
        LocalDateTime createdAt
) {
}
