package com.example.catalog.interfaces.rest;

import com.example.catalog.application.query.book.BookSummaryView;

import java.util.UUID;

record BookSummaryResponse(UUID id, String title, String author, String category,
                           long priceCents, String currency, int stock) {

    static BookSummaryResponse from(BookSummaryView view) {
        return new BookSummaryResponse(view.id(), view.title(), view.author(),
                view.category(), view.priceCents(), view.currency(), view.stock());
    }
}
