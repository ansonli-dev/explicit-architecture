package com.example.catalog.interfaces.rest;

import com.example.catalog.application.query.book.BookDetailView;

import java.util.UUID;

record BookDetailResponse(UUID id, String title, String author, String category,
                          long priceCents, String currency, int stock) {

    static BookDetailResponse from(BookDetailView view) {
        return new BookDetailResponse(view.id(), view.title(), view.author(),
                view.category(), view.priceCents(), view.currency(), view.stock());
    }
}
