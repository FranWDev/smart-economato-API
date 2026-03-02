package com.economato.inventory.dto.response;

/**
 * Indica si el déficit de stock proyectado está cubierto por pedidos en
 * tránsito
 * (estado CREATED, PENDING o REVIEW).
 */
public enum AlertResolution {
    /** Sin déficit proyectado — todo en orden. */
    OK,
    /** El déficit queda cubierto completamente por pedidos activos. */
    COVERED_BY_ORDER,
    /** Los pedidos activos reducen el déficit pero no lo eliminan. */
    PARTIALLY_COVERED,
    /** Déficit sin ningún pedido activo que lo cubra. */
    UNCOVERED
}
