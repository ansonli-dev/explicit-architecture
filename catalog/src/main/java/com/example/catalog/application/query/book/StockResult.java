package com.example.catalog.application.query.book;

import java.util.UUID;

public record StockResult(UUID bookId, int availableStock) {}
