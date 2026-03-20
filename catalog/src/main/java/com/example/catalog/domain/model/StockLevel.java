package com.example.catalog.domain.model;

/**
 * Stock level for a book.
 * total = physical copies on hand
 * reserved = copies currently reserved by pending orders
 * available = total - reserved (must never go below 0)
 */
public record StockLevel(int total, int reserved) {

    public StockLevel {
        if (total < 0)
            throw new IllegalArgumentException("Total stock must be non-negative");
        if (reserved < 0)
            throw new IllegalArgumentException("Reserved stock must be non-negative");
        if (reserved > total)
            throw new IllegalArgumentException("Reserved (" + reserved + ") cannot exceed total (" + total + ")");
    }

    public static StockLevel of(int total) {
        return new StockLevel(total, 0);
    }

    public int available() {
        return total - reserved;
    }

    public boolean canReserve(int quantity) {
        return quantity > 0 && available() >= quantity;
    }

    public StockLevel reserve(int quantity) {
        if (quantity <= 0)
            throw new IllegalArgumentException("Reserve quantity must be positive");
        if (!canReserve(quantity)) {
            throw new InsufficientStockException(
                    "Insufficient stock: available=" + available() + ", requested=" + quantity);
        }
        return new StockLevel(total, reserved + quantity);
    }

    public StockLevel release(int quantity) {
        if (quantity <= 0)
            throw new IllegalArgumentException("Release quantity must be positive");
        int newReserved = reserved - quantity;
        if (newReserved < 0)
            throw new IllegalArgumentException("Cannot release more than reserved");
        return new StockLevel(total, newReserved);
    }

    public StockLevel restock(int quantity) {
        if (quantity <= 0)
            throw new IllegalArgumentException("Restock quantity must be positive");
        return new StockLevel(total + quantity, reserved);
    }
}
