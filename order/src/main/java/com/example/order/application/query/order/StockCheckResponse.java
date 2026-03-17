package com.example.order.application.query.order;

import java.util.UUID;

public record StockCheckResponse(UUID bookId, int availableStock) {
}
