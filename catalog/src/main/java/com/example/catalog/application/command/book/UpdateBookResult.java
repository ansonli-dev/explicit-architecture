package com.example.catalog.application.command.book;

import java.util.UUID;

/**
 * Result returned by {@link UpdateBookCommandHandler} — assembled from in-memory
 * domain state, zero extra IO.
 */
public record UpdateBookResult(
        UUID id,
        String title,
        String authorName,
        String categoryName,
        long priceCents,
        String currency,
        int availableStock) {
}
