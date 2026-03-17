package com.example.order.domain.model;

import java.util.UUID;

/**
 * OrderItem entity — snapshot of book title and price at order time.
 * Not a live FK to catalog; ensures order history accuracy after catalog
 * changes.
 */
public class OrderItem {

    private final UUID id;
    private final UUID bookId;
    private final String bookTitle; // snapshot
    private final Money unitPrice; // snapshot
    private final int quantity;

    public OrderItem(UUID id, UUID bookId, String bookTitle, Money unitPrice, int quantity) {
        if (quantity <= 0)
            throw new IllegalArgumentException("OrderItem quantity must be positive");
        this.id = id;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem create(UUID bookId, String bookTitle, Money unitPrice, int quantity) {
        return new OrderItem(UUID.randomUUID(), bookId, bookTitle, unitPrice, quantity);
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookId() {
        return bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }
}
