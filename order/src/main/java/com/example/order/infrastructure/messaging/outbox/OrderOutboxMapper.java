package com.example.order.infrastructure.messaging.outbox;

import com.example.events.KafkaResourceConstants;
import com.example.events.v1.OrderCancelled;
import com.example.events.v1.OrderConfirmed;
import com.example.events.v1.OrderItem;
import com.example.events.v1.OrderPlaced;
import com.example.events.v1.OrderShipped;
import com.example.seedwork.domain.DomainEvent;
import com.example.seedwork.infrastructure.outbox.OutboxEntry;
import com.example.seedwork.infrastructure.outbox.OutboxMapper;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Converts order domain events to {@link OutboxEntry} records.
 * Stores Schema-Registry-prefixed Avro bytes so Debezium can relay them directly to Kafka
 * and consumers' KafkaAvroDeserializer can decode them without re-serialisation.
 */
@Component
public class OrderOutboxMapper implements OutboxMapper {

    private final Serializer<Object> avroSerializer;

    @SuppressWarnings("unchecked")
    public OrderOutboxMapper(ProducerFactory<String, Object> producerFactory) {
        var s = new KafkaAvroSerializer();
        s.configure(producerFactory.getConfigurationProperties(), false);
        this.avroSerializer = (Serializer<Object>) s;
    }

    @Override
    public Optional<OutboxEntry> toOutboxEntry(DomainEvent event) {
        return switch (event) {
            case com.example.order.domain.event.OrderPlaced e -> {
                var avro = OrderPlaced.newBuilder()
                        .setEventId(e.eventId().toString())
                        .setOrderId(e.orderId().value().toString())
                        .setCustomerId(e.customerId().value().toString())
                        .setCustomerEmail(e.customerEmail())
                        .setItems(e.items().stream()
                                .map(i -> new OrderItem(
                                        i.bookId().toString(), i.bookTitle(),
                                        i.quantity(), i.unitPrice().cents()))
                                .toList())
                        .setTotalCents(e.totalAmount().cents())
                        .setCurrency(e.totalAmount().currency())
                        .setOccurredAt(e.occurredAt())
                        .build();
                yield Optional.of(new OutboxEntry(
                        e.eventId(), e.orderId().value(),
                        KafkaResourceConstants.TOPIC_ORDER_PLACED,
                        avro.getClass().getName(),
                        avroSerializer.serialize(KafkaResourceConstants.TOPIC_ORDER_PLACED, avro)));
            }
            case com.example.order.domain.event.OrderCancelled e -> {
                var avro = OrderCancelled.newBuilder()
                        .setEventId(e.eventId().toString())
                        .setOrderId(e.orderId().value().toString())
                        .setCustomerId(e.customerId().value().toString())
                        .setReason(e.reason())
                        .setOccurredAt(e.occurredAt())
                        .build();
                yield Optional.of(new OutboxEntry(
                        e.eventId(), e.orderId().value(),
                        KafkaResourceConstants.TOPIC_ORDER_CANCELLED,
                        avro.getClass().getName(),
                        avroSerializer.serialize(KafkaResourceConstants.TOPIC_ORDER_CANCELLED, avro)));
            }
            case com.example.order.domain.event.OrderConfirmed e -> {
                var avro = OrderConfirmed.newBuilder()
                        .setEventId(e.eventId().toString())
                        .setOrderId(e.orderId().value().toString())
                        .setCustomerId(e.customerId().value().toString())
                        .setOccurredAt(e.occurredAt())
                        .build();
                yield Optional.of(new OutboxEntry(
                        e.eventId(), e.orderId().value(),
                        KafkaResourceConstants.TOPIC_ORDER_CONFIRMED,
                        avro.getClass().getName(),
                        avroSerializer.serialize(KafkaResourceConstants.TOPIC_ORDER_CONFIRMED, avro)));
            }
            case com.example.order.domain.event.OrderShipped e -> {
                var avro = OrderShipped.newBuilder()
                        .setEventId(e.eventId().toString())
                        .setOrderId(e.orderId().value().toString())
                        .setCustomerId(e.customerId().value().toString())
                        .setTrackingNumber(e.trackingNumber())
                        .setOccurredAt(e.occurredAt())
                        .build();
                yield Optional.of(new OutboxEntry(
                        e.eventId(), e.orderId().value(),
                        KafkaResourceConstants.TOPIC_ORDER_SHIPPED,
                        avro.getClass().getName(),
                        avroSerializer.serialize(KafkaResourceConstants.TOPIC_ORDER_SHIPPED, avro)));
            }
            default -> Optional.empty();
        };
    }
}
