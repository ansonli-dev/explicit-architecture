package com.example.catalog.interfaces.rest;

import com.example.catalog.application.command.book.ReserveStockResult;

import java.util.UUID;

record ReserveStockResponse(UUID bookId, int availableStock) {

    static ReserveStockResponse from(ReserveStockResult result) {
        return new ReserveStockResponse(result.bookId(), result.availableStock());
    }
}
