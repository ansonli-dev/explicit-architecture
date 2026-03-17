package com.example.seedwork.infrastructure.kafka;

import com.example.seedwork.infrastructure.kafka.retry.ConsumerRetryPersistenceAdapter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional processor for Kafka messages — the single place where idempotency,
 * retry persistence, and offset commit are enforced.
 *
 * <p>Being a standalone Spring bean (not a base class) means {@code @Transactional}
 * is applied via the proxy, avoiding the self-invocation problem.
 *
 * <p>Usage from a {@code @KafkaListener} method:
 * <pre>{@code
 * @KafkaListener(topics = "...", groupId = "...")
 * public void on(ConsumerRecord<String, OrderPlaced> record, Acknowledgment ack) {
 *     processor.process(orderPlacedHandler, record, ack);
 * }
 * }</pre>
 */
public class KafkaMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageProcessor.class);

    private final ProcessedEventStore processedEventStore;
    private final ConsumerRetryPersistenceAdapter retryAdapter;

    public KafkaMessageProcessor(ProcessedEventStore processedEventStore,
                                  ConsumerRetryPersistenceAdapter retryAdapter) {
        this.processedEventStore = processedEventStore;
        this.retryAdapter = retryAdapter;
    }

    /**
     * Process a Kafka record with idempotency and retry support.
     *
     * <ol>
     *   <li>Duplicate check — if already in {@code processed_events}, ack and return.</li>
     *   <li>Invoke {@code handler.handle(event)} inside the transaction.</li>
     *   <li>On success — insert into {@code processed_events}, register {@code afterCommit} ack.
     *       The offset is committed only after the DB transaction commits, closing the
     *       at-most-once window that exists when ack precedes commit.</li>
     *   <li>On failure — persist to {@code consumer_retry_events}, ack immediately
     *       (never block the partition).</li>
     * </ol>
     */
    @Transactional
    public <T extends SpecificRecord> void process(RetryableKafkaHandler<T> handler,
                                                    ConsumerRecord<?, T> record,
                                                    Acknowledgment ack) {
        T event = record.value();
        UUID eventId = handler.eventId(event);

        if (processedEventStore.isProcessed(eventId)) {
            log.debug("Duplicate event {} on topic {}, skipping", eventId, record.topic());
            ack.acknowledge();
            return;
        }

        boolean ackScheduled = false;
        try {
            handler.handle(event);
            processedEventStore.markProcessed(eventId);
            registerAfterCommitAck(ack);
            ackScheduled = true;
        } catch (Exception e) {
            log.warn("Handler failed for event {} on topic {}, persisting for retry: {}",
                    eventId, record.topic(), e.getMessage());
            persistRetry(eventId, record, handler.eventType(), event);
        } finally {
            if (!ackScheduled) {
                ack.acknowledge();
            }
        }
    }

    /**
     * Basic idempotent processing without retry persistence.
     * Used by {@link IdempotentKafkaListener} subclasses.
     * Ack is registered as an {@code afterCommit} callback — same guarantee as
     * {@link #process}.
     */
    @Transactional
    public void processSimple(ConsumerRecord<?, ?> record, Acknowledgment ack,
                               UUID eventId, Runnable handler) {
        if (processedEventStore.isProcessed(eventId)) {
            log.debug("Duplicate event {} on topic {}, skipping", eventId, record.topic());
            ack.acknowledge();
            return;
        }
        handler.run();
        processedEventStore.markProcessed(eventId);
        registerAfterCommitAck(ack);
    }

    /**
     * Registers an {@code afterCommit} callback that acknowledges the Kafka offset
     * only after the surrounding DB transaction has successfully committed.
     *
     * <p>If the ack itself fails (e.g. broker temporarily unreachable), Spring logs a
     * warning but does not propagate the exception. Kafka will re-deliver the message;
     * the {@code processed_events} record already present will cause an idempotent skip.
     */
    private static void registerAfterCommitAck(Acknowledgment ack) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ack.acknowledge();
            }
        });
    }

    private <T extends SpecificRecord> void persistRetry(UUID eventId,
                                                          ConsumerRecord<?, ?> record,
                                                          Class<T> eventType,
                                                          T event) {
        try {
            String messageKey = record.key() != null ? record.key().toString() : null;
            retryAdapter.saveNewFailure(
                    eventId, record.topic(), messageKey,
                    eventType.getName(), serialize(event),
                    "unknown", Instant.now());
        } catch (Exception ex) {
            log.error("Failed to persist retry record for event {}: {}", eventId, ex.getMessage(), ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static byte[] serialize(SpecificRecord record) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            new SpecificDatumWriter(record.getSchema()).write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize Avro record: " + record.getClass().getName(), e);
        }
    }
}
