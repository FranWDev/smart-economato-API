package com.economato.inventory.application.usecase.smg.model;

import java.math.BigDecimal;

public record OrderSnapshot(
        Integer id,
        String status,
        String supplierName,
        int itemCount,
        BigDecimal total
) {
}
