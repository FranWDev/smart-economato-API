package com.economato.inventory.infrastructure.adapter.in.web;

public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}