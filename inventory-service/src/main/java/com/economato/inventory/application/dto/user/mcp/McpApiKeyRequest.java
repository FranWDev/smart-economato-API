package com.economato.inventory.application.dto.user.mcp;

public record McpApiKeyRequest(
        String provider,
        String apiKey
) {
}
