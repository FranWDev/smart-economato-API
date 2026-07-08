package com.economato.inventory.infrastructure.adapter.in.web.shared;

public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}