package com.example.order.application.command.order;

import com.example.seedwork.application.command.Command;

import java.util.List;
import java.util.UUID;

public record PlaceOrderCommand(
        UUID customerId,
        String customerEmail,
        List<OrderItem> items) implements Command<PlaceOrderResult> {

    public record OrderItem(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {}
}
