package com.economato.inventory.domain.model;

public enum MovementType {
    ENTRADA("Entrada de stock"),
    SALIDA("Salida de stock"),
    MODIFICACION("Modificación de inventario"),
    RECEPCION("Recepción de mercancía"),
    CREACION("Creación de producto"),
    OCULTAR("Producto ocultado"),
    MOSTRAR("Producto mostrado");

    private final String description;

    MovementType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
