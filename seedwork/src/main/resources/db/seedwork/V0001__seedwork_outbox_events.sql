-- Transactional Outbox table (seedwork)
-- Relay: OutboxRelayScheduler polls unpublished rows and publishes to Kafka.
-- SKIP LOCKED is used during relay to allow multiple pods without duplicate sends.

CREATE TABLE IF NOT EXISTS outbox_event (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    event_id        UUID        NOT NULL,
    aggregate_id    UUID        NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    message_key     VARCHAR(100) NOT NULL,
    avro_class_name VARCHAR(300) NOT NULL,
    avro_payload    BYTEA       NOT NULL,
    published       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox_event (created_at)
    WHERE published = FALSE;
