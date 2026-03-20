package com.example.order.domain.model;

import java.util.UUID;

/**
 * OrderItem value object — snapshot of book title and price at order time.
 * Not a live FK to catalog; ensures order history accuracy after catalog
 * changes.
 */
public record OrderItem(UUID id, UUID bookId, String bookTitle, Money unitPrice, int quantity) {

    public OrderItem {
        if (quantity <= 0)
            throw new IllegalArgumentException("OrderItem quantity must be positive");
    }

    public static OrderItem create(UUID bookId, String bookTitle, Money unitPrice, int quantity) {
        return new OrderItem(UUID.randomUUID(), bookId, bookTitle, unitPrice, quantity);
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
