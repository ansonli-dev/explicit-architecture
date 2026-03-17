package com.example.order.domain.model;

import com.example.seedwork.domain.DomainId;

import java.util.UUID;

public record CustomerId(UUID value) implements DomainId<UUID> {

    public CustomerId {
        if (value == null)
            throw new IllegalArgumentException("CustomerId must not be null");
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    public static CustomerId from(String value) {
        return new CustomerId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
