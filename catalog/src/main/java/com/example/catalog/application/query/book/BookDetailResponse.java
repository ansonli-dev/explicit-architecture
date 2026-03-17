package com.example.catalog.application.query.book;
import java.util.UUID;
public record BookDetailResponse(UUID id, String title, String author, String category, long priceCents, String currency, int stock) {}
