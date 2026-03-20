package com.example.catalog.application.command.book;

import com.example.seedwork.application.command.Command;

import java.util.UUID;

public record UpdateBookCommand(
        UUID id,
        String title,
        String authorName,
        String authorBiography,
        Long priceCents,
        String currency,
        Integer restockQuantity) implements Command<UpdateBookResult> {

    public UpdateBookCommand {
        if (id == null)
            throw new IllegalArgumentException("Book id must not be null");
        // Biography update requires a name; partial author update is inconsistent.
        if (authorBiography != null && authorName == null)
            throw new IllegalArgumentException("authorName is required when authorBiography is provided");
        // Price fields must always be supplied together.
        if ((priceCents == null) != (currency == null))
            throw new IllegalArgumentException("priceCents and currency must be provided together");
    }
}
