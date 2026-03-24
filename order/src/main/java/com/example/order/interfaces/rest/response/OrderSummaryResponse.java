package com.example.order.interfaces.rest.response;

import com.example.order.application.query.order.OrderSummaryResult;

import java.util.UUID;

public record OrderSummaryResponse(UUID orderId, UUID customerId, String status, long totalCents, String currency) {

    public static OrderSummaryResponse from(OrderSummaryResult result) {
        return new OrderSummaryResponse(result.orderId(), result.customerId(), result.status(),
                result.totalCents(), result.currency());
    }
}
