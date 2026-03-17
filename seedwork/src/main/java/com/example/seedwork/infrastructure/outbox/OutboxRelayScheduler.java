package com.example.seedwork.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls unpublished outbox entries on a fixed schedule, publishes each via
 * {@link KafkaOutboxEventPublisher}, and marks them published on success.
 *
 * <p>At-least-once delivery — consumers must be idempotent on {@code eventId}.
 * Configure poll interval via {@code outbox.relay.interval-ms} (default 5 000 ms).
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaOutboxEventPublisher publisher;

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:5000}")
    @Transactional
    public void relay() {
        List<OutboxEventJpaEntity> pending = outboxJpaRepository.findUnpublished();
        if (pending.isEmpty()) return;

        log.debug("[OutboxRelay] processing {} pending entries", pending.size());

        for (OutboxEventJpaEntity entry : pending) {
            try {
                publisher.publish(entry);
                outboxJpaRepository.markPublished(entry.getId());
                log.info("[OutboxRelay] published topic={} eventId={} key={}",
                        entry.getTopic(), entry.getEventId(), entry.getMessageKey());
            } catch (Exception e) {
                log.error("[OutboxRelay] failed topic={} eventId={}: {}",
                        entry.getTopic(), entry.getEventId(), e.getMessage());
            }
        }
    }
}
