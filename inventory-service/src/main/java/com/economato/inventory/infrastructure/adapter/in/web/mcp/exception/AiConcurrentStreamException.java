package com.economato.inventory.infrastructure.adapter.in.web.mcp.exception;

public class AiConcurrentStreamException extends RuntimeException {
    public AiConcurrentStreamException(String message) {
        super(message);
    }
}
