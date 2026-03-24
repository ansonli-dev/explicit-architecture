package com.example.order.interfaces.rest.response;

import com.example.order.application.query.order.OrderDetailResult;
import com.example.order.application.query.order.OrderItemResult;

import java.util.List;
import java.util.UUID;

public record OrderDetailResponse(UUID orderId, UUID customerId, String customerEmail,
                                  String status, List<OrderItemResponse> items, long totalCents, String currency) {

    public record OrderItemResponse(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {}

    public static OrderDetailResponse from(OrderDetailResult result) {
        List<OrderItemResponse> items = result.items().stream()
                .map(i -> new OrderItemResponse(i.bookId(), i.bookTitle(), i.unitPriceCents(), i.currency(), i.quantity()))
                .toList();
        return new OrderDetailResponse(result.orderId(), result.customerId(), result.customerEmail(),
                result.status(), items, result.totalCents(), result.currency());
    }
}
