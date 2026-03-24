package com.example.order.application.query.order;

import java.util.List;
import java.util.UUID;

public record OrderDetailResult(
        UUID orderId,
        UUID customerId,
        String customerEmail,
        String status,
        List<OrderItemResult> items,
        long totalCents,
        String currency) {
}
