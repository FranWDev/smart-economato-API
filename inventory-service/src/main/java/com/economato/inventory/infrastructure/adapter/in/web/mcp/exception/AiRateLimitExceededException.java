package com.economato.inventory.infrastructure.adapter.in.web.mcp.exception;

public class AiRateLimitExceededException extends RuntimeException {
    public AiRateLimitExceededException(String message) {
        super(message);
    }
}
