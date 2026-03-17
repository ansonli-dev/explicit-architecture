package com.example.seedwork.infrastructure.kafka.retry;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence operations for consumer retry events.
 * Used by IdempotentKafkaListener (on failure) and RetryScheduler (on retry outcome).
 */
public class ConsumerRetryPersistenceAdapter {

    private final ConsumerRetryEventJpaRepository repository;

    ConsumerRetryPersistenceAdapter(ConsumerRetryEventJpaRepository repository) {
        this.repository = repository;
    }

    public void saveNewFailure(UUID eventId, String topic, String messageKey,
                               String avroClassName, byte[] avroPayload,
                               String consumerGroup, Instant nextRetryAt) {
        repository.save(ConsumerRetryEventJpaEntity.newFailure(
                eventId, topic, messageKey, avroClassName, avroPayload, consumerGroup, nextRetryAt));
    }

    public void incrementAttempt(UUID id, int newAttemptCount,
                                 Instant lastFailedAt, Instant nextRetryAt) {
        ConsumerRetryEventJpaEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("ConsumerRetryEvent not found: " + id));
        entity.setAttemptCount(newAttemptCount);
        entity.setLastFailedAt(lastFailedAt);
        entity.setNextRetryAt(nextRetryAt);
        repository.save(entity);
    }

    public void markDeadLettered(UUID id, Instant deadLetteredAt) {
        ConsumerRetryEventJpaEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("ConsumerRetryEvent not found: " + id));
        entity.setDeadLettered(true);
        entity.setDeadLetteredAt(deadLetteredAt);
        repository.save(entity);
    }

    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
