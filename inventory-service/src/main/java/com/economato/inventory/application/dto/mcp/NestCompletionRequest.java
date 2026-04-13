package com.economato.inventory.application.dto.mcp;

public record NestCompletionRequest(
        String compressedContext,
        String apiKey,
        String provider,
        String userName,
        String userLanguage,
        String model
) {
}
