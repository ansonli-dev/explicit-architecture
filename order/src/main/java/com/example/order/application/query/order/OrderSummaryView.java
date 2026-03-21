package com.example.order.application.query.order;

import java.util.UUID;

public record OrderSummaryView(UUID orderId, UUID customerId, String status, long totalCents, String currency) {
}
