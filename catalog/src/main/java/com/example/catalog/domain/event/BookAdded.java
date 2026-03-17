package com.example.catalog.domain.event;

import com.example.seedwork.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record BookAdded(UUID eventId, UUID bookId, String title, Instant occurredAt)
        implements DomainEvent {
}
