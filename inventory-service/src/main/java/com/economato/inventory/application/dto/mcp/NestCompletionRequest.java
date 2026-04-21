package com.economato.inventory.application.dto.mcp;

public record NestCompletionRequest(
        String compressedContext,
        String systemPrompt,
        String apiKey,
        String provider,
        String userName,
        String userLanguage,
        String model
) {
    public NestCompletionRequest(String compressedContext,
            String apiKey,
            String provider,
            String userName,
            String userLanguage,
            String model) {
        this(compressedContext, null, apiKey, provider, userName, userLanguage, model);
    }
}
