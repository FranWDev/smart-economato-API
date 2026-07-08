package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception;

public class AiConcurrentStreamException extends RuntimeException {
    public AiConcurrentStreamException(String message) {
        super(message);
    }
}
