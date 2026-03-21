package com.example.catalog.application.command.book;

import java.util.UUID;

public record UpdateBookCommand(
        UUID id,
        String title,
        String authorName,
        String authorBiography,
        Long priceCents,
        String currency,
        Integer restockQuantity) {

    public UpdateBookCommand {
        if (id == null)
            throw new IllegalArgumentException("Book id must not be null");
        if (authorBiography != null && authorName == null)
            throw new IllegalArgumentException("authorName is required when authorBiography is provided");
        if ((priceCents == null) != (currency == null))
            throw new IllegalArgumentException("priceCents and currency must be provided together");
    }
}
