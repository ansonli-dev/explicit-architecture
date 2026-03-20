package com.example.catalog.application.command.book;

import java.util.UUID;

/**
 * Result returned by {@link AddBookCommandHandler} — assembled from in-memory
 * domain state, zero extra IO.
 */
public record AddBookResult(
        UUID id,
        String title,
        String authorName,
        String categoryName,
        long priceCents,
        String currency,
        int availableStock) {
}
