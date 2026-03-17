package com.example.catalog.domain.model;

import com.example.seedwork.domain.DomainId;

import java.util.UUID;

public record BookId(UUID value) implements DomainId<UUID> {

    public BookId {
        if (value == null)
            throw new IllegalArgumentException("BookId value must not be null");
    }

    public static BookId of(UUID value) {
        return new BookId(value);
    }

    public static BookId generate() {
        return new BookId(UUID.randomUUID());
    }

    public static BookId from(String value) {
        return new BookId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
