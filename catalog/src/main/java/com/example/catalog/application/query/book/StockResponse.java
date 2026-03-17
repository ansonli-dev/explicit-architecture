package com.example.catalog.application.query.book;
import java.util.UUID;
public record StockResponse(UUID bookId, int availableStock) {}
