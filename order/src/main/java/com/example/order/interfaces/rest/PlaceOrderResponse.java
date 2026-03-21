package com.example.order.interfaces.rest;

import com.example.order.application.command.order.PlaceOrderResult;

import java.util.List;
import java.util.UUID;

record PlaceOrderResponse(UUID orderId, UUID customerId, String customerEmail,
                          String status, List<Item> items, long totalCents, String currency) {

    record Item(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {}

    static PlaceOrderResponse from(PlaceOrderResult result) {
        List<Item> items = result.items().stream()
                .map(i -> new Item(i.bookId(), i.bookTitle(), i.unitPriceCents(), i.currency(), i.quantity()))
                .toList();
        return new PlaceOrderResponse(result.orderId(), result.customerId(), result.customerEmail(),
                result.status(), items, result.totalCents(), result.currency());
    }
}
