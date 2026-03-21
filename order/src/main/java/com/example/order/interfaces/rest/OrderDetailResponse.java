package com.example.order.interfaces.rest;

import com.example.order.application.query.order.OrderDetailView;
import com.example.order.application.query.order.OrderItemView;

import java.util.List;
import java.util.UUID;

record OrderDetailResponse(UUID orderId, UUID customerId, String customerEmail,
                           String status, List<OrderItemResponse> items, long totalCents, String currency) {

    record OrderItemResponse(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {}

    static OrderDetailResponse from(OrderDetailView view) {
        List<OrderItemResponse> items = view.items().stream()
                .map(i -> new OrderItemResponse(i.bookId(), i.bookTitle(), i.unitPriceCents(), i.currency(), i.quantity()))
                .toList();
        return new OrderDetailResponse(view.orderId(), view.customerId(), view.customerEmail(),
                view.status(), items, view.totalCents(), view.currency());
    }
}
