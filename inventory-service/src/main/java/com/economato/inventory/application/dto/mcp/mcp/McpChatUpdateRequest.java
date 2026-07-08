package com.economato.inventory.application.dto.mcp.mcp;

import jakarta.validation.constraints.Size;

public record McpChatUpdateRequest(
        @Size(max = 255) String title
) {
}
