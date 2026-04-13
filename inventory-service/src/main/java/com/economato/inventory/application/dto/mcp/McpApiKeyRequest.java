package com.economato.inventory.application.dto.mcp;

public record McpApiKeyRequest(
        String provider,
        String apiKey
) {
}
