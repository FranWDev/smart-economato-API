package com.economato.inventory.infrastructure.adapter.in.web.shared;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}