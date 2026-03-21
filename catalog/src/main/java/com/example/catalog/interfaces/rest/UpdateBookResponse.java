package com.example.catalog.interfaces.rest;

import com.example.catalog.application.command.book.UpdateBookResult;

import java.util.UUID;

record UpdateBookResponse(UUID id, String title, String authorName, String categoryName,
                          long priceCents, String currency, int availableStock) {

    static UpdateBookResponse from(UpdateBookResult result) {
        return new UpdateBookResponse(result.id(), result.title(), result.authorName(),
                result.categoryName(), result.priceCents(), result.currency(), result.availableStock());
    }
}
