package com.example.catalog.infrastructure.messaging.outbox;

import com.example.catalog.domain.event.StockReleased;
import com.example.catalog.domain.event.StockReserved;
import com.example.events.KafkaResourceConstants;
import com.example.seedwork.domain.DomainEvent;
import com.example.seedwork.infrastructure.outbox.OutboxEntry;
import com.example.seedwork.infrastructure.outbox.OutboxMapper;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Converts catalog domain events to {@link OutboxEntry} records for the outbox table.
 * Stores Schema-Registry-prefixed Avro bytes so Debezium can relay them directly to Kafka
 * and consumers' KafkaAvroDeserializer can decode them without re-serialisation.
 */
@Component
public class CatalogOutboxMapper implements OutboxMapper {

    private final Serializer<Object> avroSerializer;

    @SuppressWarnings("unchecked")
    public CatalogOutboxMapper(ProducerFactory<String, Object> producerFactory) {
        var s = new KafkaAvroSerializer();
        s.configure(producerFactory.getConfigurationProperties(), false);
        this.avroSerializer = (Serializer<Object>) s;
    }

    @Override
    public Optional<OutboxEntry> toOutboxEntry(DomainEvent event) {
        return switch (event) {
            case StockReserved e -> {
                var avro = com.example.events.v1.StockReserved.newBuilder()
                        .setEventId(e.eventId().toString())
                        .setBookId(e.bookId().toString())
                        .setOrderId(e.orderId().toString())
                        .setQuantity(e.quantity())
                        .setOccurredAt(e.occurredAt())
                        .build();
                yield Optional.of(new OutboxEntry(
                        e.eventId(), e.bookId(),
                        KafkaResourceConstants.TOPIC_STOCK_RESERVED,
                        avro.getClass().getName(),
                        avroSerializer.serialize(KafkaResourceConstants.TOPIC_STOCK_RESERVED, avro)));
            }
            case StockReleased e -> {
                var avro = com.example.events.v1.StockReleased.newBuilder()
                        .setEventId(e.eventId().toString())
                        .setBookId(e.bookId().toString())
                        .setOrderId(e.orderId().toString())
                        .setQuantity(e.quantity())
                        .setOccurredAt(e.occurredAt())
                        .build();
                yield Optional.of(new OutboxEntry(
                        e.eventId(), e.bookId(),
                        KafkaResourceConstants.TOPIC_STOCK_RELEASED,
                        avro.getClass().getName(),
                        avroSerializer.serialize(KafkaResourceConstants.TOPIC_STOCK_RELEASED, avro)));
            }
            default -> Optional.empty();
        };
    }
}
