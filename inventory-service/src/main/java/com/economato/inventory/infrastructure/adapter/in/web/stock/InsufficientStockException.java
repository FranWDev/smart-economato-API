package com.economato.inventory.infrastructure.adapter.in.web.stock;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}