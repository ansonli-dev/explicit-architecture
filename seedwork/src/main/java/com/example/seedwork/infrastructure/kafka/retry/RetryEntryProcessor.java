package com.example.seedwork.infrastructure.kafka.retry;

import com.example.seedwork.infrastructure.kafka.ProcessedEventStore;
import com.example.seedwork.infrastructure.kafka.RetryHandlerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles per-entry retry processing with correct transaction isolation.
 *
 * <p><b>Claim pattern</b> ({@link #claimBatch}): a short transaction fetches due entries
 * via {@code SELECT FOR UPDATE SKIP LOCKED} and atomically advances their
 * {@code nextRetryAt} to {@code now + claimDurationMs}. Once committed, the rows are
 * invisible to other scheduler instances until the claim expires — even though the
 * database locks have already been released. This prevents duplicate processing across
 * pods without holding long-lived locks.
 *
 * <p><b>Per-entry isolation</b> ({@link #processEntry}): each entry runs in its own
 * {@code REQUIRES_NEW} transaction. A handler failure — which normally marks the outer
 * transaction as rollback-only — only rolls back that single entry. Other entries in the
 * same scan batch are unaffected. On failure, {@code nextRetryAt} is overwritten with the
 * real exponential-backoff timestamp, replacing the claim timestamp.
 *
 * <p><b>Crash safety</b>: if a pod dies after claiming but before completing, the claim
 * expires naturally and the entry becomes visible again for the next scan cycle.
 *
 * <p><b>Dead-letter notification</b>: when an entry exceeds {@code maxAttempts}, a
 * {@link DeadLetteredEvent} is published via {@link ApplicationEventPublisher} after the
 * {@code REQUIRES_NEW} transaction commits. Services subscribe to this event to implement
 * alerting or DLQ forwarding.
 */
class RetryEntryProcessor {

    private static final Logger log = LoggerFactory.getLogger(RetryEntryProcessor.class);

    private final ConsumerRetryEventJpaRepository retryRepository;
    private final ProcessedEventStore processedEventStore;
    private final ConsumerRetryPersistenceAdapter retryAdapter;
    private final RetryHandlerRegistry handlerRegistry;
    private final RetryProperties props;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;

    RetryEntryProcessor(ConsumerRetryEventJpaRepository retryRepository,
                        ProcessedEventStore processedEventStore,
                        ConsumerRetryPersistenceAdapter retryAdapter,
                        RetryHandlerRegistry handlerRegistry,
                        RetryProperties props,
                        MeterRegistry meterRegistry,
                        ApplicationEventPublisher eventPublisher) {
        this.retryRepository = retryRepository;
        this.processedEventStore = processedEventStore;
        this.retryAdapter = retryAdapter;
        this.handlerRegistry = handlerRegistry;
        this.props = props;
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Fetches due entries with SKIP LOCKED and claims them by advancing
     * {@code nextRetryAt} to {@code now + claimDurationMs}.
     * Commits immediately — keeping the claim alive without holding row locks.
     *
     * @return IDs of claimed entries to pass to {@link #processEntry}
     */
    @Transactional
    List<UUID> claimBatch() {
        List<ConsumerRetryEventJpaEntity> due = retryRepository.findDueForRetry(
                Instant.now(), props.maxAttempts(), props.batchSize());
        if (due.isEmpty()) return List.of();

        Instant claimedUntil = Instant.now().plusMillis(props.claimDurationMs());
        due.forEach(e -> e.setNextRetryAt(claimedUntil));
        // JPA dirty-checking flushes the nextRetryAt update on commit
        return due.stream().map(ConsumerRetryEventJpaEntity::getId).toList();
    }

    /**
     * Processes one claimed entry in an isolated {@code REQUIRES_NEW} transaction.
     * A handler exception only rolls back this entry's transaction; sibling entries
     * in the same scan batch are not affected.
     *
     * <p>On success: entry is deleted and the event ID is marked as processed.
     * On failure: {@code nextRetryAt} is updated to the real backoff time, replacing
     * the claim timestamp so the entry re-enters the normal retry window.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void processEntry(UUID entryId) {
        ConsumerRetryEventJpaEntity entry = retryRepository.findById(entryId).orElse(null);
        if (entry == null) return; // already deleted (e.g. by a concurrent instance after claim expiry)

        if (processedEventStore.isProcessed(entry.getEventId())) {
            log.debug("Retry event {} already processed, cleaning up", entry.getEventId());
            retryAdapter.deleteById(entry.getId());
            return;
        }

        try {
            SpecificRecord record = deserialize(entry.getAvroClassName(), entry.getAvroPayload());
            handlerRegistry.dispatch(entry.getAvroClassName(), record);
            retryAdapter.deleteById(entry.getId());
            processedEventStore.markProcessed(entry.getEventId());
            log.info("Retry succeeded for event {} (attempt {})", entry.getEventId(), entry.getAttemptCount());
        } catch (Exception e) {
            handleFailure(entry, e);
        }
    }

    private void handleFailure(ConsumerRetryEventJpaEntity entry, Exception cause) {
        int newAttempt = entry.getAttemptCount() + 1;
        if (newAttempt > props.maxAttempts()) {
            Instant now = Instant.now();
            retryAdapter.markDeadLettered(entry.getId(), now);
            meterRegistry.counter("seedwork.kafka.retry.dead_lettered",
                    "topic", entry.getTopic()).increment();
            log.error("Event {} dead-lettered after {} attempts: {}",
                    entry.getEventId(), entry.getAttemptCount(), cause.getMessage(), cause);
            // Published after REQUIRES_NEW commits — listeners see consistent DB state
            eventPublisher.publishEvent(new DeadLetteredEvent(
                    entry.getEventId(),
                    entry.getTopic(),
                    entry.getAvroClassName(),
                    entry.getAttemptCount(),
                    now,
                    cause.getMessage()
            ));
        } else {
            // overwrite the claim timestamp with the real backoff window
            Instant nextRetry = computeNextRetry(newAttempt);
            retryAdapter.incrementAttempt(entry.getId(), newAttempt, Instant.now(), nextRetry);
            log.warn("Retry failed for event {} (attempt {}/{}), next retry at {}",
                    entry.getEventId(), newAttempt, props.maxAttempts(), nextRetry);
        }
    }

    /**
     * Exponential backoff capped at {@code maxMs}: {@code min(baseMs * 2^(attemptCount-1), maxMs)}.
     */
    Instant computeNextRetry(int attemptCount) {
        long baseMs = props.backoff().baseMs();
        long maxMs  = props.backoff().maxMs();
        long shift  = Math.min(attemptCount - 1, 62);
        long delayMs = Math.min(baseMs << shift, maxMs);
        return Instant.now().plusMillis(delayMs);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SpecificRecord deserialize(String avroClassName, byte[] payload) throws Exception {
        Class<?> clazz = Class.forName(avroClassName);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
        return (SpecificRecord) new SpecificDatumReader(clazz).read(null, decoder);
    }
}
