package com.example.order.domain.event;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;

import com.example.seedwork.domain.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Emitted when an order is successfully placed (Pending state). */
public record OrderPlaced(
        UUID eventId,
        OrderId orderId,
        CustomerId customerId,
        String customerEmail,
        List<OrderItem> items,
        Money totalAmount,
        Instant occurredAt) implements DomainEvent {
}
