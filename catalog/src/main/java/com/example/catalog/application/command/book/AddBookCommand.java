package com.example.catalog.application.command.book;

import com.example.seedwork.application.command.Command;

public record AddBookCommand(
        String title,
        String authorName,
        String authorBiography,
        long priceCents,
        String currency,
        String categoryName,
        int initialStock) implements Command<AddBookResult> {
}
