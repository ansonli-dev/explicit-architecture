package com.example.catalog.interfaces.rest.response;

import com.example.catalog.application.query.book.StockResult;

import java.util.UUID;

public record StockResponse(UUID bookId, int availableStock) {

    public static StockResponse from(StockResult result) {
        return new StockResponse(result.bookId(), result.availableStock());
    }
}
