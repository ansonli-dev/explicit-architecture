package com.example.order.domain.event;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;

import com.example.seedwork.domain.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Emitted when an order is cancelled. Carries item snapshots for event-driven stock release. */
public record OrderCancelled(UUID eventId, OrderId orderId, CustomerId customerId,
        String reason, List<OrderItem> items, Instant occurredAt) implements DomainEvent {
}
