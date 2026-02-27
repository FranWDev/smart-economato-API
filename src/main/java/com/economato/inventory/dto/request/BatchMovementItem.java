package com.economato.inventory.dto.request;

import com.economato.inventory.model.MovementType;

import java.math.BigDecimal;

public class BatchMovementItem {
    private final Integer productId;
    private final BigDecimal quantityDelta;
    private final MovementType movementType;
    private final String description;

    public BatchMovementItem(Integer productId, BigDecimal quantityDelta,
            MovementType movementType, String description) {
        this.productId = productId;
        this.quantityDelta = quantityDelta;
        this.movementType = movementType;
        this.description = description;
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
}
