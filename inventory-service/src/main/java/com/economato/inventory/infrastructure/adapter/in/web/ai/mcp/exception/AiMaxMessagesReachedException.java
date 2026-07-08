package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception;

public class AiMaxMessagesReachedException extends RuntimeException {
    public AiMaxMessagesReachedException(String message) {
        super(message);
    }
}
