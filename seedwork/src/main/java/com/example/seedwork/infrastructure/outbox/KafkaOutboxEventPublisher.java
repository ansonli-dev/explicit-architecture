package com.example.seedwork.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Relays outbox entries to Kafka by sending the stored bytes as-is.
 *
 * <p>The {@link OutboxMapper} is responsible for serializing events into the wire
 * format (e.g. Confluent Avro: magic byte + schema-id + Avro binary). This publisher
 * treats those bytes as an opaque payload and forwards them directly to Kafka using
 * a byte-array-valued {@link KafkaTemplate}. Consumers use their own deserializer
 * (e.g. {@code KafkaAvroDeserializer}) to decode the wire-format bytes.
 *
 * <p>This design avoids a fragile deserialize-then-re-serialize round-trip and keeps
 * seedwork free of schema-registry dependencies.
 */
@RequiredArgsConstructor
public class KafkaOutboxEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public void publish(OutboxEventJpaEntity entry) throws Exception {
        kafkaTemplate.send(entry.getTopic(), entry.getMessageKey(), entry.getAvroPayload()).get();
    }
}
