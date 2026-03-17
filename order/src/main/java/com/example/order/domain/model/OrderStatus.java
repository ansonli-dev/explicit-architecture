package com.example.order.domain.model;

/**
 * Sealed interface representing the lifecycle states of an Order.
 * Java 21 pattern matching enables exhaustive switch expressions on
 * OrderStatus.
 */
public sealed interface OrderStatus permits
        OrderStatus.Pending,
        OrderStatus.Confirmed,
        OrderStatus.Shipped,
        OrderStatus.Cancelled {

    record Pending() implements OrderStatus {
    }

    record Confirmed() implements OrderStatus {
    }

    record Shipped(String trackingNumber) implements OrderStatus {
    }

    record Cancelled(String reason) implements OrderStatus {
    }

    default String name() {
        return switch (this) {
            case Pending ignored -> "PENDING";
            case Confirmed ignored -> "CONFIRMED";
            case Shipped ignored -> "SHIPPED";
            case Cancelled ignored -> "CANCELLED";
        };
    }
}
