package com.example.seedwork.infrastructure.kafka.retry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Kafka consumer retry mechanism.
 *
 * <pre>
 * consumer:
 *   retry:
 *     enabled: true
 *     interval-ms: 10000
 *     max-attempts: 5
 *     batch-size: 100
 *     backoff:
 *       base-ms: 1000
 *       max-ms: 3600000
 * </pre>
 */
@ConfigurationProperties(prefix = "consumer.retry")
public record RetryProperties(
        boolean enabled,
        long intervalMs,
        int maxAttempts,
        int batchSize,
        /** How long (ms) a claimed entry is invisible to other scheduler instances. */
        long claimDurationMs,
        Backoff backoff
) {
    public RetryProperties() {
        this(true, 10_000, 5, 100, 300_000, new Backoff(1_000, 3_600_000));
    }

    public record Backoff(long baseMs, long maxMs) {}
}
