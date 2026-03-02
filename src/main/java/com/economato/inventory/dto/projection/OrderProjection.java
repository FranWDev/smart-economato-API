package com.economato.inventory.dto.projection;

import com.economato.inventory.model.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderProjection {
    Integer getId();

    UserInfo getUser();

    LocalDateTime getOrderDate();

    OrderStatus getStatus();

    List<OrderDetailSummary> getDetails();

    interface UserInfo {
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

            BigDecimal getUnitPrice();
        }
    }
}
