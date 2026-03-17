package com.example.seedwork.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;
/**
 * Optional base class for Kafka consumers that need basic idempotency without retry.
 *
 * <p>For retry support, inject {@link KafkaMessageProcessor} directly and implement
 * {@link RetryableKafkaHandler} per event type instead.
 *
 * <p>Usage:
 * <pre>{@code
 * @Component
 * public class MyConsumer extends IdempotentKafkaListener {
 *
 *     public MyConsumer(KafkaMessageProcessor processor) { super(processor); }
 *
 *     @KafkaListener(topics = "...", groupId = "...")
 *     public void onEvent(ConsumerRecord<String, MyEvent> record, Acknowledgment ack) {
 *         handle(record, ack, UUID.fromString(record.value().getEventId().toString()), () -> {
 *             // business logic
 *         });
 *     }
 * }
 * }</pre>
 */
public abstract class IdempotentKafkaListener {

    private final KafkaMessageProcessor processor;

    protected IdempotentKafkaListener(KafkaMessageProcessor processor) {
        this.processor = processor;
    }

    /**
     * Checks idempotency, delegates to {@code handler}, then commits offset.
     * Transactional behaviour is provided by {@link KafkaMessageProcessor}.
     */
    protected void handle(ConsumerRecord<?, ?> record, Acknowledgment ack, UUID eventId, Runnable handler) {
        processor.processSimple(record, ack, eventId, handler);
    }
}
