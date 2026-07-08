package com.economato.inventory.application.usecase.smg.model.product;

import java.math.BigDecimal;

public record ProductSnapshot(
        Integer id,
        String name,
        BigDecimal stock,
        String unit,
        BigDecimal price,
        String alertLevel,
        BigDecimal prediction14d,
        Integer daysToExpiry
) {
}
