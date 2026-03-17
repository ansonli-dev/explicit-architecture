package com.example.seedwork.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic JPA entity for the transactional outbox table.
 *
 * <p>{@code avro_payload} stores Avro binary bytes ({@code SpecificRecord.toByteBuffer().array()}).
 * {@code avro_class_name} is the fully-qualified Avro class name used by the relay
 * to reconstruct the record via reflection — no switch/case needed anywhere in seedwork.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventJpaEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    @Column(name = "avro_class_name", nullable = false, length = 300)
    private String avroClassName;

    @Column(name = "avro_payload", nullable = false, columnDefinition = "BYTEA")
    private byte[] avroPayload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OutboxEventJpaEntity(UUID eventId, UUID aggregateId, String topic,
                                String messageKey, String avroClassName, byte[] avroPayload) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.avroClassName = avroClassName;
        this.avroPayload = avroPayload;
        this.published = false;
        this.createdAt = Instant.now();
    }

}
