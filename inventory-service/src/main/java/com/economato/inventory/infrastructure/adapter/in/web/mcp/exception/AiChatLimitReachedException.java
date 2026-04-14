package com.economato.inventory.infrastructure.adapter.in.web.mcp.exception;

public class AiChatLimitReachedException extends RuntimeException {
    public AiChatLimitReachedException(String message) {
        super(message);
    }
}
