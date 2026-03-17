package com.example.seedwork.infrastructure.outbox;

import java.util.UUID;

/**
 * Carries everything needed to persist one outbox row and later relay it to Kafka.
 *
 * @param eventId       idempotency key (UUID from the domain event)
 * @param aggregateId   aggregate that raised the event — used as Kafka message key
 * @param topic         destination Kafka topic
 * @param avroClassName fully-qualified class name of the Avro record (e.g. {@code com.example.events.v1.OrderPlaced})
 *                      used by the relay to reconstruct the record via reflection + {@code fromByteBuffer}
 * @param avroPayload   Avro binary-encoded bytes ({@code SpecificRecord.toByteBuffer().array()})
 */
public record OutboxEntry(UUID eventId, UUID aggregateId, String topic,
                          String avroClassName, byte[] avroPayload) {
}
