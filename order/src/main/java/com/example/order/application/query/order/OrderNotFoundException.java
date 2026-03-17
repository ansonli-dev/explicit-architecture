package com.example.order.application.query.order;

import com.example.seedwork.domain.NotFoundException;

import java.util.UUID;

public class OrderNotFoundException extends NotFoundException {
    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
