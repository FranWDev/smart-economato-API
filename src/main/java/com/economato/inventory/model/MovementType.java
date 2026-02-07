package com.economato.inventory.model;

public enum MovementType {
    ENTRADA("Entrada de stock"),
    SALIDA("Salida de stock"),
    AJUSTE("Ajuste de inventario");

    private final String description;

    MovementType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
