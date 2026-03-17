-- V1: Order bounded context schema (write model)

CREATE TABLE IF NOT EXISTS orders (
    id             UUID PRIMARY KEY,
    customer_id    UUID         NOT NULL,
    customer_email VARCHAR(320) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    total_cents    BIGINT       NOT NULL,
    currency       CHAR(3)      NOT NULL DEFAULT 'CNY',
    tracking_number VARCHAR(100),
    cancel_reason  TEXT
);

CREATE TABLE IF NOT EXISTS order_item (
    id              UUID PRIMARY KEY,
    order_id        UUID         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    book_id         UUID         NOT NULL,
    book_title      VARCHAR(500) NOT NULL,
    unit_price_cents BIGINT      NOT NULL,
    currency        CHAR(3)      NOT NULL DEFAULT 'CNY',
    quantity        INT          NOT NULL CHECK (quantity > 0)
);

ALTER TABLE orders REPLICA IDENTITY FULL;
