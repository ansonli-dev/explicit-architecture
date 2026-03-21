package com.example.catalog.interfaces.rest.response;

import com.example.catalog.application.command.book.UpdateBookResult;

import java.util.UUID;

public record UpdateBookResponse(UUID id, String title, String authorName, String categoryName,
                                 long priceCents, String currency, int availableStock) {

    public static UpdateBookResponse from(UpdateBookResult result) {
        return new UpdateBookResponse(result.id(), result.title(), result.authorName(),
                result.categoryName(), result.priceCents(), result.currency(), result.availableStock());
    }
}
