package com.example.seedwork.infrastructure.outbox;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers a {@code seedwork.outbox.unpublished} gauge that tracks the number of
 * unpublished outbox rows. The gauge is lazily evaluated on each Prometheus scrape,
 * so it always reflects the current database state without additional polling.
 *
 * <p>Use this metric to alert on relay lag — e.g., fire an alert when the gauge
 * exceeds a threshold for more than N minutes.
 */
class OutboxMetrics {

    OutboxMetrics(OutboxJpaRepository repo, MeterRegistry meterRegistry) {
        meterRegistry.gauge("seedwork.outbox.unpublished", repo,
                r -> r.countByPublishedFalse());
    }
}
