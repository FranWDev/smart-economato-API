package com.economato.inventory.application.dto.mcp;

import java.time.LocalDateTime;

public record McpChatMessageResponseDto(
        Long id,
        String role,
        String content,
        String toolName,
        String toolResult,
        int inputTokens,
        int outputTokens,
        LocalDateTime createdAt
) {
}
