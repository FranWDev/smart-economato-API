package com.economato.inventory.application.dto.projection;

import com.economato.inventory.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderProjection {
    Integer getId();

    UserInfo getUser();

    SupplierInfo getSupplier();

    LocalDateTime getOrderDate();

    OrderStatus getStatus();

    List<OrderDetailSummary> getDetails();

    interface UserInfo {
        Integer getId();

        String getName();
    }

    interface SupplierInfo {
        Integer getId();

        String getName();
    }

    interface OrderDetailSummary {
        BigDecimal getQuantity();

        BigDecimal getQuantityReceived();

        ProductInfo getProduct();

        interface ProductInfo {
            Integer getId();

            String getName();

            String getUnit();

            BigDecimal getUnitPrice();

            BigDecimal getLotQuantity();
        }
    }
}
