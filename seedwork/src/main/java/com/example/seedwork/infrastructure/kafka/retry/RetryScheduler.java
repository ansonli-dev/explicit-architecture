package com.example.seedwork.infrastructure.kafka.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.UUID;

/**
 * Scheduled entry point for the consumer retry mechanism.
 *
 * <p>Delegates all work to {@link RetryEntryProcessor} to ensure correct
 * transaction isolation: claiming is a short atomic transaction; each entry
 * is processed in its own {@code REQUIRES_NEW} transaction.
 *
 * @see RetryEntryProcessor
 */
public class RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetryScheduler.class);

    private final RetryEntryProcessor entryProcessor;

    RetryScheduler(RetryEntryProcessor entryProcessor) {
        this.entryProcessor = entryProcessor;
    }

    @Scheduled(fixedDelayString = "${consumer.retry.interval-ms:10000}")
    public void scan() {
        List<UUID> claimed = entryProcessor.claimBatch();
        if (claimed.isEmpty()) return;

        log.debug("[RetryScheduler] claimed {} entries", claimed.size());
        for (UUID id : claimed) {
            entryProcessor.processEntry(id);
        }
    }
}
