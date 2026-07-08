package com.economato.inventory.infrastructure.adapter.in.web.shared;

public class AuditException extends RuntimeException {
    public AuditException(String message) {
        super(message);
    }
}
