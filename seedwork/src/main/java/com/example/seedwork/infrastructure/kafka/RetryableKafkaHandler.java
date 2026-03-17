package com.example.seedwork.infrastructure.kafka;

import org.apache.avro.specific.SpecificRecord;

import java.util.UUID;

/**
 * Contract for a Kafka message handler that supports idempotency and retry.
 *
 * <p>Implement this interface once per event type. The framework ({@link KafkaMessageProcessor})
 * handles idempotency checks, Avro serialization, retry persistence, and offset commits
 * automatically — no boilerplate in the implementing class.
 *
 * <pre>{@code
 * @Component
 * public class OrderPlacedHandler implements RetryableKafkaHandler<OrderPlaced> {
 *
 *     public Class<OrderPlaced> eventType() { return OrderPlaced.class; }
 *
 *     public UUID eventId(OrderPlaced event) {
 *         return UUID.fromString(event.getEventId().toString());
 *     }
 *
 *     public void handle(OrderPlaced event) {
 *         // pure business logic
 *     }
 * }
 * }</pre>
 *
 * @param <T> the Avro-generated event type
 */
public interface RetryableKafkaHandler<T extends SpecificRecord> {

    /** The Avro event class this handler processes. Used for deserialization during retry. */
    Class<T> eventType();

    /** Extract the idempotency key from the event. */
    UUID eventId(T event);

    /** Execute the business logic for this event. May throw on transient failures. */
    void handle(T event);
}
