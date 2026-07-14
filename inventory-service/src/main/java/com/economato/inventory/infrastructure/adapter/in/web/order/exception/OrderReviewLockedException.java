package com.economato.inventory.infrastructure.adapter.in.web.order.exception;

import lombok.Getter;

@Getter
public class OrderReviewLockedException extends RuntimeException {

    private final Integer orderId;
    private final String lockedBy;

    public OrderReviewLockedException(Integer orderId, String lockedBy) {
        super(String.format("%s esta revisando esta orden", lockedBy));
        this.orderId = orderId;
        this.lockedBy = lockedBy;
    }
}
