package com.example.catalog.application.command.book;

import com.example.catalog.application.query.book.BookDetailResponse;
import com.example.seedwork.application.command.Command;

import java.util.UUID;

public record UpdateBookCommand(
        UUID id,
        String title,
        String authorName,
        String authorBiography,
        Long priceCents,
        String currency,
        Integer restockQuantity) implements Command<BookDetailResponse> {
}
