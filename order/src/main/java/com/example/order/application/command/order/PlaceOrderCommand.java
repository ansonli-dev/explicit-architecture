package com.example.order.application.command.order;

import com.example.seedwork.application.command.Command;
import com.example.order.application.query.order.OrderDetailResponse;

import java.util.List;
import java.util.UUID;

public record PlaceOrderCommand(
        UUID customerId,
        String customerEmail,
        List<OrderItemRequest> items) implements Command<OrderDetailResponse> {

    public record OrderItemRequest(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {
    }
}
