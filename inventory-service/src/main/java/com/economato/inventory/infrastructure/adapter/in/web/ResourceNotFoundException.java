package com.economato.inventory.infrastructure.adapter.in.web;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}