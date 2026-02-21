package com.economato.inventory.dto.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface InventoryAuditProjection {
    Integer getId();

    BigDecimal getQuantity();

    String getMovementType();

    String getActionDescription();

    String getPreviousState();

    String getNewState();

    LocalDateTime getMovementDate();

    ProductInfo getProduct();

    UserInfo getUsers();

    interface ProductInfo {
        Integer getId();

        String getName();

        BigDecimal getCurrentStock();
    }

    interface UserInfo {
        Integer getId();

        String getName();
    }
}
