package com.economato.inventory.infrastructure.adapter.in.web.mcp.exception;

public class AiMaxMessagesReachedException extends RuntimeException {
    public AiMaxMessagesReachedException(String message) {
        super(message);
    }
}
