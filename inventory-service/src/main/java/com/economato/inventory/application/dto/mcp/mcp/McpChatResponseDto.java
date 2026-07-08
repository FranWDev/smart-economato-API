package com.economato.inventory.application.dto.mcp.mcp;

import java.time.LocalDateTime;

public record McpChatResponseDto(
        Long id,
        String title,
        String status,
        String activeProvider,
        String userLanguage,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt,
        int messageCount
) {
}
