package com.economato.inventory.application.dto.request;

import com.economato.inventory.domain.model.MovementType;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BatchMovementItem {
    private final Integer productId;
    private final BigDecimal quantityDelta;
    private final MovementType movementType;
    private final String description;
    private final LocalDate expirationDate;

    public BatchMovementItem(Integer productId, BigDecimal quantityDelta,
            MovementType movementType, String description, LocalDate expirationDate) {
        this.productId = productId;
        this.quantityDelta = quantityDelta;
        this.movementType = movementType;
        this.description = description;
        this.expirationDate = expirationDate;
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
}
