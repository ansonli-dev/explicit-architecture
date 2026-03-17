package com.example.seedwork.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for in-process domain events.
 *
 * <p>Implement this on a Java record whose components include {@code eventId}
 * and {@code occurredAt}; the record auto-generates the required accessors:
 *
 * <pre>{@code
 * public record OrderPlaced(UUID eventId, OrderId orderId, ..., Instant occurredAt)
 *         implements DomainEvent { }
 * }</pre>
 *
 * <p>This interface lives in the domain layer of seedwork — zero framework dependencies.
 */
public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();
}
