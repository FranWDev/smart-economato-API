package com.economato.inventory.application.dto.user.mcp;

import java.time.LocalDateTime;

public record McpApiKeyResponseDto(
        Long id,
        String provider,
        String keyHint,
        boolean active,
        LocalDateTime createdAt
) {
}
