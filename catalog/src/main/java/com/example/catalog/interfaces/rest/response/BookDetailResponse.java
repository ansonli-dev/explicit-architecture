package com.example.catalog.interfaces.rest.response;

import com.example.catalog.application.query.book.BookDetailResult;

import java.util.UUID;

public record BookDetailResponse(UUID id, String title, String author, String category,
                                 long priceCents, String currency, int stock) {

    public static BookDetailResponse from(BookDetailResult result) {
        return new BookDetailResponse(result.id(), result.title(), result.author(),
                result.category(), result.priceCents(), result.currency(), result.stock());
    }
}
