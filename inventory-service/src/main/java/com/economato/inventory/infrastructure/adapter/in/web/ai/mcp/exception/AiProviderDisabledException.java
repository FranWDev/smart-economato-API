package com.economato.inventory.infrastructure.adapter.in.web.ai.mcp.exception;

public class AiProviderDisabledException extends RuntimeException {
    public AiProviderDisabledException(String message) {
        super(message);
    }
}
