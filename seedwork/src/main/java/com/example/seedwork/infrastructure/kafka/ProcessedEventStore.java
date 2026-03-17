package com.example.seedwork.infrastructure.kafka;

import java.util.UUID;

/**
 * Public facade over the package-private processed_events persistence.
 * Used by the retry sub-package to mark events as successfully processed
 * and to check idempotency without exposing JPA internals.
 */
public class ProcessedEventStore {

    private final ProcessedEventJpaRepository repository;

    ProcessedEventStore(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @SuppressWarnings("null")
    public boolean isProcessed(UUID eventId) {
        return repository.existsById(eventId);
    }

    @SuppressWarnings("null")
    public void markProcessed(UUID eventId) {
        repository.save(ProcessedEventJpaEntity.of(eventId));
    }
}
