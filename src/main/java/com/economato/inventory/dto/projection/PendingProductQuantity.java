package com.economato.inventory.dto.projection;

import java.math.BigDecimal;

/**
 * Proyección de resultado de la query nativa que suma las cantidades
 * pendientes de recibir por producto en pedidos activos
 * (estado CREATED, PENDING o REVIEW).
 */
public interface PendingProductQuantity {

    /** ID del producto. */
    Integer getProductId();

    /** Cantidad total solicitada en pedidos activos. */
    BigDecimal getPendingQuantity();
}
