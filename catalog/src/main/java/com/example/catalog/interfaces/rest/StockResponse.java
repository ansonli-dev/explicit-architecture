package com.example.catalog.interfaces.rest;

import com.example.catalog.application.query.book.StockView;

import java.util.UUID;

record StockResponse(UUID bookId, int availableStock) {

    static StockResponse from(StockView view) {
        return new StockResponse(view.bookId(), view.availableStock());
    }
}
