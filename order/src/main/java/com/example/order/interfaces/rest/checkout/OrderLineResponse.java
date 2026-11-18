package com.example.order.interfaces.rest.checkout;

import com.example.order.application.checkout.Checkout;

import java.util.UUID;

record OrderLineResponse(UUID bookId, String title, int quantity, long unitPriceMinor) {
    static OrderLineResponse from(Checkout.Line line) {
        return new OrderLineResponse(line.bookId(), line.title(), line.quantity(), line.unitPriceMinor());
    }
}
