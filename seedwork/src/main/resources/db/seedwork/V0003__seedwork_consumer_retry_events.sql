-- Kafka consumer retry table (seedwork)
-- Stores failed Kafka events for scheduled retry with exponential backoff.
-- Claim pattern: next_retry_at is advanced atomically on claim to prevent
-- duplicate processing across pods (SKIP LOCKED + short-lived claim window).

CREATE TABLE IF NOT EXISTS consumer_retry_events (
    id              UUID        NOT NULL PRIMARY KEY,
    event_id        UUID        NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    message_key     VARCHAR(100),
    avro_class_name VARCHAR(300) NOT NULL,
    avro_payload    BYTEA       NOT NULL,
    consumer_group  VARCHAR(200) NOT NULL,
    attempt_count   INT         NOT NULL DEFAULT 1,
    last_failed_at  TIMESTAMPTZ NOT NULL,
    next_retry_at   TIMESTAMPTZ NOT NULL,
    dead_lettered   BOOLEAN     NOT NULL DEFAULT FALSE,
    dead_lettered_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_retry_event_id UNIQUE (event_id)
);

-- Used by RetryEntryProcessor.claimBatch() with SKIP LOCKED
CREATE INDEX IF NOT EXISTS idx_retry_due
    ON consumer_retry_events (next_retry_at)
    WHERE dead_lettered = FALSE;
