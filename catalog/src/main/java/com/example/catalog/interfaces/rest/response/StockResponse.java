package com.example.catalog.interfaces.rest.response;

import com.example.catalog.application.query.book.StockView;

import java.util.UUID;

public record StockResponse(UUID bookId, int availableStock) {

    public static StockResponse from(StockView view) {
        return new StockResponse(view.bookId(), view.availableStock());
    }
}
