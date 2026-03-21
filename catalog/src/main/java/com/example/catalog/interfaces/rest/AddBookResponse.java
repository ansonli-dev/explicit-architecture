package com.example.catalog.interfaces.rest;

import com.example.catalog.application.command.book.AddBookResult;

import java.util.UUID;

record AddBookResponse(UUID id, String title, String authorName, String categoryName,
                       long priceCents, String currency, int availableStock) {

    static AddBookResponse from(AddBookResult result) {
        return new AddBookResponse(result.id(), result.title(), result.authorName(),
                result.categoryName(), result.priceCents(), result.currency(), result.availableStock());
    }
}
