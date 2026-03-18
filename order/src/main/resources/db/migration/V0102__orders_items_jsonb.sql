-- V0102: Denormalize order_item into orders.items (jsonb).
-- Motivation: Debezium CDC on the orders table needs a single row to contain the
-- complete order snapshot. Storing items as jsonb achieves this without joins.

-- 1. Add items column
ALTER TABLE orders ADD COLUMN IF NOT EXISTS items jsonb NOT NULL DEFAULT '[]';

-- 2. Migrate existing rows: aggregate order_item rows into a jsonb array per order
UPDATE orders o
SET items = (
    SELECT COALESCE(jsonb_agg(
        jsonb_build_object(
            'id',               i.id,
            'book_id',          i.book_id,
            'book_title',       i.book_title,
            'unit_price_cents', i.unit_price_cents,
            'currency',         i.currency,
            'quantity',         i.quantity
        )
    ), '[]'::jsonb)
    FROM order_item i
    WHERE i.order_id = o.id
);

-- 3. Remove default now that data is migrated
ALTER TABLE orders ALTER COLUMN items DROP DEFAULT;

-- 4. Drop the now-redundant order_item table
DROP TABLE IF EXISTS order_item;

-- Ensure REPLICA IDENTITY FULL is still set (already set in V0100, but be explicit)
ALTER TABLE orders REPLICA IDENTITY FULL;
