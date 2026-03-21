package com.example.order.application.query.order;

import java.util.List;
import java.util.UUID;

public record OrderDetailView(
        UUID orderId,
        UUID customerId,
        String customerEmail,
        String status,
        List<OrderItemView> items,
        long totalCents,
        String currency) {
}
