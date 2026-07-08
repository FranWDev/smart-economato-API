package com.economato.inventory.application.dto.order.projection;

import java.time.LocalDateTime;

public interface OrderAuditProjection {
    Integer getId();

    String getAction();

    String getDetails();

    String getPreviousState();

    String getNewState();

    LocalDateTime getAuditDate();

    OrderInfo getOrder();

    UserInfo getUser();

    interface OrderInfo {
        Integer getId();
    }

    interface UserInfo {
        Integer getId();

        String getName();
    }
}
