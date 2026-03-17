package com.example.seedwork.infrastructure.outbox;

import com.example.seedwork.domain.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes an outbox row atomically within the same transaction as the aggregate save.
 * Payload is Avro binary — no intermediate JSON DTO.
 */
@Slf4j
@RequiredArgsConstructor
public class OutboxWriteListener {

    private final OutboxJpaRepository outboxJpaRepository;
    private final OutboxMapper outboxMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        outboxMapper.toOutboxEntry(event).ifPresent(entry -> {
            outboxJpaRepository.save(new OutboxEventJpaEntity(
                    entry.eventId(), entry.aggregateId(),
                    entry.topic(), entry.aggregateId().toString(),
                    entry.avroClassName(), entry.avroPayload()));
            log.debug("[OutboxWrite] saved topic={} class={} eventId={}",
                    entry.topic(), entry.avroClassName(), entry.eventId());
        });
    }
}
