package com.example.order.domain.event;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.OrderId;

import com.example.seedwork.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/** Emitted when an order transitions from Pending to Confirmed. */
public record OrderConfirmed(UUID eventId, OrderId orderId, CustomerId customerId, Instant occurredAt)
        implements DomainEvent {
}
