package com.economato.inventory.dto.projection;

import java.math.BigDecimal;

public interface OrderDetailProjection {

    BigDecimal getQuantity();

    BigDecimal getQuantityReceived();

    OrderInfo getOrder();

    ProductInfo getProduct();

    interface OrderInfo {
        Integer getId();
    }

    interface ProductInfo {
        Integer getId();

        String getName();

        BigDecimal getUnitPrice();
    }
}
