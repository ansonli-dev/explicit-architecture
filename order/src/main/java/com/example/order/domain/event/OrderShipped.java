package com.example.order.domain.event;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.OrderId;

import com.example.seedwork.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Emitted when an order is shipped. */
public record OrderShipped(UUID eventId, OrderId orderId, CustomerId customerId,
        String trackingNumber, Instant occurredAt) implements DomainEvent {
}
