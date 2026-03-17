package com.example.order.application.query.order;

import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(
        UUID orderId,
        UUID customerId,
        String customerEmail,
        String status,
        List<OrderItemResponse> items,
        long totalCents,
        String currency) {
}
