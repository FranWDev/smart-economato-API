package com.economato.inventory.exception;

public class AuditException extends RuntimeException {
    public AuditException(String message) {
        super(message);
    }
}
