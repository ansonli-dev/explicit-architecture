package com.example.catalog.domain.event;

import com.example.seedwork.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record StockReserved(UUID eventId, UUID bookId, UUID orderId, int quantity, Instant occurredAt)
        implements DomainEvent {
}
