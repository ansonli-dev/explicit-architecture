package com.example.order.interfaces.rest;

import com.example.order.application.query.order.OrderSummaryView;

import java.util.UUID;

record OrderSummaryResponse(UUID orderId, UUID customerId, String status, long totalCents, String currency) {

    static OrderSummaryResponse from(OrderSummaryView view) {
        return new OrderSummaryResponse(view.orderId(), view.customerId(), view.status(),
                view.totalCents(), view.currency());
    }
}
