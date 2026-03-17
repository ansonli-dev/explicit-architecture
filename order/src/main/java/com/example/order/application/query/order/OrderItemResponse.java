package com.example.order.application.query.order;

import java.util.UUID;

public record OrderItemResponse(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {
}
