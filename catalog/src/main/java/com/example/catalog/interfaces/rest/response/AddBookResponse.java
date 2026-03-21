package com.example.catalog.interfaces.rest.response;

import com.example.catalog.application.command.book.AddBookResult;

import java.util.UUID;

public record AddBookResponse(UUID id, String title, String authorName, String categoryName,
                              long priceCents, String currency, int availableStock) {

    public static AddBookResponse from(AddBookResult result) {
        return new AddBookResponse(result.id(), result.title(), result.authorName(),
                result.categoryName(), result.priceCents(), result.currency(), result.availableStock());
    }
}
