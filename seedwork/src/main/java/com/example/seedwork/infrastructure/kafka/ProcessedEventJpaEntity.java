package com.example.seedwork.infrastructure.kafka;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {}

    static ProcessedEventJpaEntity of(UUID eventId) {
        ProcessedEventJpaEntity e = new ProcessedEventJpaEntity();
        e.eventId = eventId;
        e.processedAt = Instant.now();
        return e;
    }
}
