-- Kafka consumer idempotency table (seedwork)
-- Stores successfully processed event IDs to deduplicate Kafka at-least-once redeliveries.
-- Inserted atomically with the handler's own DB write (same REQUIRES_NEW transaction).

CREATE TABLE IF NOT EXISTS processed_events (
    event_id     UUID        NOT NULL PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
