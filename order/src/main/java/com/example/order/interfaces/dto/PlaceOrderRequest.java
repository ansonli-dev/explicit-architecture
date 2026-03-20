package com.example.order.interfaces.dto;

import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(UUID customerId, String customerEmail, List<Item> items) {
    public record Item(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {}
}
