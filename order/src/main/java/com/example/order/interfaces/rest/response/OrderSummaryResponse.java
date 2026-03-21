package com.example.order.interfaces.rest.response;

import com.example.order.application.query.order.OrderSummaryView;

import java.util.UUID;

public record OrderSummaryResponse(UUID orderId, UUID customerId, String status, long totalCents, String currency) {

    public static OrderSummaryResponse from(OrderSummaryView view) {
        return new OrderSummaryResponse(view.orderId(), view.customerId(), view.status(),
                view.totalCents(), view.currency());
    }
}
