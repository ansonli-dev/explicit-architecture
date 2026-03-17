package com.example.order.application.query.order;

import java.util.UUID;

public record OrderResponse(UUID orderId, UUID customerId, String status, long totalCents, String currency) {
}
