package com.economato.gateway.domain.model;

import java.time.Instant;

public record FallbackResponse(
        String timestamp,
        int status,
        String error,
        String code,
        String message
) {
    public static FallbackResponse create(int status, String error, String code, String message) {
        return new FallbackResponse(
                Instant.now().toString(),
                status,
                error,
                code,
                message
        );
    }
}
