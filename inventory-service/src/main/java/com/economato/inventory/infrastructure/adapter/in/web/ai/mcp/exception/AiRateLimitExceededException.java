package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception;

public class AiRateLimitExceededException extends RuntimeException {
    public AiRateLimitExceededException(String message) {
        super(message);
    }
}
