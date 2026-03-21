package com.example.catalog.application.command.book;

public record AddBookCommand(
        String title,
        String authorName,
        String authorBiography,
        long priceCents,
        String currency,
        String categoryName,
        int initialStock) {
}
