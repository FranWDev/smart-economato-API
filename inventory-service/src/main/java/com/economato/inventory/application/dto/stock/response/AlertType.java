package com.economato.inventory.application.dto.stock.response;

public enum AlertType {
    /** Alerta basada en consumo proyectado por IA. */
    PREDICTION,
    /** Alerta solo por caducidad de lotes — sin predicción de consumo. */
    EXPIRATION,
    /** Alerta combinada: predicción de consumo + lotes próximos a caducar. */
    COMBINED
}