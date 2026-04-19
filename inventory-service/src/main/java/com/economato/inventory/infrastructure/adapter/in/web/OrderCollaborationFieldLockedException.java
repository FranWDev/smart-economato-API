package com.economato.inventory.infrastructure.adapter.in.web;

import lombok.Getter;

@Getter
public class OrderCollaborationFieldLockedException extends RuntimeException {

    private final Integer orderId;
    private final String fieldPath;
    private final String lockedBy;

    public OrderCollaborationFieldLockedException(String message, Integer orderId, String fieldPath, String lockedBy) {
        super(message);
        this.orderId = orderId;
        this.fieldPath = fieldPath;
        this.lockedBy = lockedBy;
    }
}
