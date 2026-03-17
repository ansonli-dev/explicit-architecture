package com.example.seedwork.infrastructure.kafka.retry;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring {@code ApplicationEvent} published when a retry entry exceeds
 * {@code consumer.retry.max-attempts} and is marked as dead-lettered.
 *
 * <p>Services subscribe to this event to implement their own dead-letter
 * handling strategy — alerting, forwarding to a DLQ Kafka topic, etc.:
 *
 * <pre>{@code
 * @EventListener
 * public void onDeadLettered(DeadLetteredEvent event) {
 *     alertingService.sendAlert("Dead-lettered event: " + event.eventId());
 * }
 * }</pre>
 *
 * <p>The event is published <em>after</em> the {@code REQUIRES_NEW} transaction
 * that marks the row as dead-lettered has committed, so the database state is
 * always consistent when listeners receive it.
 */
public record DeadLetteredEvent(
        UUID eventId,
        String topic,
        String avroClassName,
        int attemptCount,
        Instant deadLetteredAt,
        String failureCause
) {}
