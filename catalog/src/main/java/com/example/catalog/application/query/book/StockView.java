package com.example.catalog.application.query.book;

import java.util.UUID;

public record StockView(UUID bookId, int availableStock) {}
