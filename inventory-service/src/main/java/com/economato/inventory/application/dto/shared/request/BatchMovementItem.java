package com.economato.inventory.application.dto.shared.request;

import com.economato.inventory.domain.model.shared.MovementType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BatchMovementItem {
    private final Integer productId;
    private final BigDecimal quantityDelta;
    private final MovementType movementType;
    private final String description;
    private final LocalDate expirationDate;
    private final String correlationId;

    // Vintage constructor (retrocompatibilidad)
    public BatchMovementItem(Integer productId, BigDecimal quantityDelta,
            MovementType movementType, String description, LocalDate expirationDate) {
        this(productId, quantityDelta, movementType, description, expirationDate, null);
    }

    // Nuevo constructor con correlationId
    public BatchMovementItem(Integer productId, BigDecimal quantityDelta,
            MovementType movementType, String description, LocalDate expirationDate, String correlationId) {
        this.productId = productId;
        this.quantityDelta = quantityDelta;
        this.movementType = movementType;
        this.description = description;
        this.expirationDate = expirationDate;
        this.correlationId = correlationId;
    }

    public Integer getProductId() {
        return productId;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
