package com.example.order.domain.model;

import com.example.seedwork.domain.DomainId;

import java.util.UUID;

public record OrderId(UUID value) implements DomainId<UUID> {

    public OrderId {
        if (value == null)
            throw new IllegalArgumentException("OrderId must not be null");
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID());
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }

    public static OrderId from(String value) {
        return new OrderId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
