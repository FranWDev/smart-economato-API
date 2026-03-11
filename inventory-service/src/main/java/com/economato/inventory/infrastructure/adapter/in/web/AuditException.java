package com.economato.inventory.infrastructure.adapter.in.web;

public class AuditException extends RuntimeException {
    public AuditException(String message) {
        super(message);
    }
}
