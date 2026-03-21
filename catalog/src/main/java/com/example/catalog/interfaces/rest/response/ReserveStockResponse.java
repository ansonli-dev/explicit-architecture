package com.example.catalog.interfaces.rest.response;

import com.example.catalog.application.command.book.ReserveStockResult;

import java.util.UUID;

public record ReserveStockResponse(UUID bookId, int availableStock) {

    public static ReserveStockResponse from(ReserveStockResult result) {
        return new ReserveStockResponse(result.bookId(), result.availableStock());
    }
}
