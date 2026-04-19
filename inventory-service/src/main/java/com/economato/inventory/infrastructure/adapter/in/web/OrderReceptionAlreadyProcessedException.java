package com.economato.inventory.infrastructure.adapter.in.web;

import com.economato.inventory.domain.model.OrderStatus;

public class OrderReceptionAlreadyProcessedException extends RuntimeException {

    private final Integer orderId;
    private final OrderStatus currentStatus;

    public OrderReceptionAlreadyProcessedException(Integer orderId, OrderStatus currentStatus) {
        super(buildMessage(orderId, currentStatus));
        this.orderId = orderId;
        this.currentStatus = currentStatus;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    private static String buildMessage(Integer orderId, OrderStatus currentStatus) {
        String statusText = currentStatus == OrderStatus.CONFIRMED ? "confirmada" : "incompleta";
        return "La orden #" + orderId + " ya esta " + statusText + ".";
    }
}