package com.economato.inventory.domain.model;

public enum MovementType {
    ENTRADA("Entrada de stock"),
    SALIDA("Salida de stock"),
    AJUSTE("Ajuste de inventario"),          // Legacy — kept for DB compatibility
    MODIFICACION("Modificación de inventario"),
    RECEPCION("Recepción de mercancía"),
    CREACION("Creación de producto"),
    OCULTAR("Producto ocultado"),
    MOSTRAR("Producto mostrado"),
    CUARENTENA("Cuarentena de stock"),
    MERMA("Retirada de stock/Merma"),
    REVERSION("Reversión de movimiento");


    private final String description;

    MovementType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
