package com.example.seedwork.infrastructure.outbox;

import com.example.seedwork.domain.DomainEvent;

import java.util.Optional;

/**
 * Single SPI that each service implements to plug into the outbox infrastructure.
 *
 * <p>Only one responsibility: convert a domain event to an {@link OutboxEntry}
 * containing Avro binary bytes, the target Kafka topic, message key, and the
 * fully-qualified Avro class name. The relay reconstructs the record via
 * reflection — no switch/case needed in the service.
 *
 * <p>Example:
 * <pre>{@code
 * case OrderPlaced e -> {
 *     var avro = com.example.events.v1.OrderPlaced.newBuilder()...build();
 *     yield Optional.of(new OutboxEntry(
 *         e.eventId(), e.orderId().value(),
 *         KafkaResourceConstants.TOPIC_ORDER_PLACED,
 *         avro.getClass().getName(),          // e.g. "com.example.events.v1.OrderPlaced"
 *         avro.toByteBuffer().array()));
 * }
 * }</pre>
 */
public interface OutboxMapper {

    Optional<OutboxEntry> toOutboxEntry(DomainEvent event);
}
