package com.economato.inventory.application.dto.product.projection;

import java.math.BigDecimal;

public interface ProductProjection {
    Integer getId();

    String getName();


    String getUnit();

    BigDecimal getUnitPrice();

    String getProductCode();

    BigDecimal getCurrentStock();

    BigDecimal getAvailabilityPercentage();

    BigDecimal getLotQuantity();


    boolean getIsHidden();

    SupplierProjection getSupplier();
}
