package com.example.catalog.application.command.book;

import java.util.UUID;

/**
 * Result returned by {@link ReserveStockCommandHandler} — assembled from in-memory
 * domain state, zero extra IO.
 */
public record ReserveStockResult(UUID bookId, int availableStock) {}
