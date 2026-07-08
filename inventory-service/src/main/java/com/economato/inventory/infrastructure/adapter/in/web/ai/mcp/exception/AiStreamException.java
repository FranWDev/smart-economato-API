package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception;

public class AiStreamException extends RuntimeException {

    public AiStreamException(String message) {
        super(message);
    }

    public AiStreamException(String message, Throwable cause) {
        super(message, cause);
    }
}