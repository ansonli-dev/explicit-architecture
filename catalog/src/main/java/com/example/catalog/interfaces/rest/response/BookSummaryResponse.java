package com.example.catalog.interfaces.rest.response;

import com.example.catalog.application.query.book.BookSummaryResult;

import java.util.UUID;

public record BookSummaryResponse(UUID id, String title, String author, String category,
                                  long priceCents, String currency, int stock) {

    public static BookSummaryResponse from(BookSummaryResult result) {
        return new BookSummaryResponse(result.id(), result.title(), result.author(),
                result.category(), result.priceCents(), result.currency(), result.stock());
    }
}
