ALTER TABLE orders
    ADD COLUMN created_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN cancellation_expected_version BIGINT;

-- Historical placements and cancellations were written with their outbox events.
UPDATE orders o
SET created_at = history.placed_at,
    cancelled_at = CASE WHEN o.status = 'CANCELLED' THEN history.cancelled_at END
FROM (
    SELECT aggregate_id,
        min(created_at) FILTER (WHERE topic = 'bookstore.order.placed') AS placed_at,
        min(created_at) FILTER (WHERE topic = 'bookstore.order.cancelled') AS cancelled_at
    FROM outbox_event
    WHERE topic IN ('bookstore.order.placed', 'bookstore.order.cancelled')
    GROUP BY aggregate_id
) history
WHERE o.id = history.aggregate_id;

ALTER TABLE orders ALTER COLUMN created_at SET DEFAULT clock_timestamp();

DROP INDEX orders_customer_id_id;
CREATE INDEX orders_customer_created_id ON orders (customer_id, created_at DESC NULLS LAST, id DESC);
