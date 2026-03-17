-- V1: Notification bounded context schema

CREATE TABLE IF NOT EXISTS notification (
    id              UUID PRIMARY KEY,
    customer_id     UUID         NOT NULL,
    recipient_email VARCHAR(320),
    channel         VARCHAR(20)  NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    body            TEXT         NOT NULL,
    delivery_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    failure_reason  TEXT
);

CREATE INDEX IF NOT EXISTS idx_notification_customer ON notification (customer_id);
