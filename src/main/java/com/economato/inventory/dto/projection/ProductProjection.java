package com.economato.inventory.dto.projection;

import java.math.BigDecimal;

public interface ProductProjection {
    Integer getId();

    String getName();

    String getType();

    String getUnit();

    BigDecimal getUnitPrice();

    String getProductCode();

    BigDecimal getCurrentStock();

    BigDecimal getAvailabilityPercentage();

    BigDecimal getMinimumStock();

    SupplierProjection getSupplier();
}
