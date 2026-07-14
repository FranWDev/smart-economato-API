package com.economato.inventory.infrastructure.adapter.in.web.shared.exception;

public class AuditException extends RuntimeException {
    public AuditException(String message) {
        super(message);
    }
}
