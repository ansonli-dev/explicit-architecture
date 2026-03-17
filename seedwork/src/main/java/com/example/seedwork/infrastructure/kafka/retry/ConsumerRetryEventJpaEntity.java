package com.example.seedwork.infrastructure.kafka.retry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumer_retry_events")
class ConsumerRetryEventJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "message_key", length = 100)
    private String messageKey;

    @Column(name = "avro_class_name", nullable = false, length = 300)
    private String avroClassName;

    @Column(name = "avro_payload", nullable = false)
    private byte[] avroPayload;

    @Column(name = "consumer_group", nullable = false, length = 200)
    private String consumerGroup;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_failed_at", nullable = false)
    private Instant lastFailedAt;

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "dead_lettered", nullable = false)
    private boolean deadLettered;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConsumerRetryEventJpaEntity() {}

    static ConsumerRetryEventJpaEntity newFailure(UUID eventId, String topic, String messageKey,
                                                   String avroClassName, byte[] avroPayload,
                                                   String consumerGroup, Instant nextRetryAt) {
        ConsumerRetryEventJpaEntity e = new ConsumerRetryEventJpaEntity();
        e.id = UUID.randomUUID();
        e.eventId = eventId;
        e.topic = topic;
        e.messageKey = messageKey;
        e.avroClassName = avroClassName;
        e.avroPayload = avroPayload;
        e.consumerGroup = consumerGroup;
        e.attemptCount = 1;
        e.lastFailedAt = Instant.now();
        e.nextRetryAt = nextRetryAt;
        e.deadLettered = false;
        e.createdAt = Instant.now();
        return e;
    }

    UUID getId()            { return id; }
    UUID getEventId()       { return eventId; }
    String getTopic()       { return topic; }
    String getAvroClassName() { return avroClassName; }
    byte[] getAvroPayload() { return avroPayload; }
    String getConsumerGroup() { return consumerGroup; }
    int getAttemptCount()   { return attemptCount; }

    void setAttemptCount(int attemptCount)   { this.attemptCount = attemptCount; }
    void setLastFailedAt(Instant lastFailedAt) { this.lastFailedAt = lastFailedAt; }
    void setNextRetryAt(Instant nextRetryAt)  { this.nextRetryAt = nextRetryAt; }
    void setDeadLettered(boolean deadLettered) { this.deadLettered = deadLettered; }
    void setDeadLetteredAt(Instant deadLetteredAt) { this.deadLetteredAt = deadLetteredAt; }
}
