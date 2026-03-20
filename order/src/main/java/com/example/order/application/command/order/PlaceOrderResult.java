package com.example.order.application.command.order;

import java.util.List;
import java.util.UUID;

/**
 * Result returned by PlaceOrderCommandHandler.
 * Independent of the read-side OrderDetailResponse — assembled from
 * in-memory domain state, no extra IO.
 */
public record PlaceOrderResult(
        UUID orderId,
        UUID customerId,
        String customerEmail,
        String status,
        List<Item> items,
        long totalCents,
        String currency) {

    public record Item(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {}
}
