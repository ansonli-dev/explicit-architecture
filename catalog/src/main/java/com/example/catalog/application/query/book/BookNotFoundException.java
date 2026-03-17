package com.example.catalog.application.query.book;

import com.example.seedwork.domain.NotFoundException;

import java.util.UUID;

public class BookNotFoundException extends NotFoundException {
    private final UUID id;

    public BookNotFoundException(UUID id) {
        super("Book not found: " + id);
        this.id = id;
    }

    public UUID getBookId() {
        return id;
    }
}
