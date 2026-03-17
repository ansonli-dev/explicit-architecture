-- V1: Catalog bounded context schema
-- Managed by Flyway; wal_level=logical set at cluster level (PostgreSQL Helm chart)

CREATE TABLE IF NOT EXISTS category (
    id   UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS book (
    id               UUID PRIMARY KEY,
    title            VARCHAR(500) NOT NULL,
    author_name      VARCHAR(200) NOT NULL,
    author_biography TEXT,
    price_cents      BIGINT       NOT NULL CHECK (price_cents >= 0),
    currency         CHAR(3)      NOT NULL DEFAULT 'CNY',
    category_id      UUID         NOT NULL REFERENCES category(id),
    stock_total      INT          NOT NULL CHECK (stock_total >= 0),
    stock_reserved   INT          NOT NULL CHECK (stock_reserved >= 0),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_stock CHECK (stock_reserved <= stock_total)
);

-- Enable logical replication for Debezium CDC
ALTER TABLE book REPLICA IDENTITY FULL;
