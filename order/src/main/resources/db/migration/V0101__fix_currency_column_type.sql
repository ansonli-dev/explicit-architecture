-- V5: Fix currency columns from CHAR(3) to VARCHAR(3) to match Hibernate entity mapping
ALTER TABLE orders     ALTER COLUMN currency TYPE VARCHAR(3);
ALTER TABLE order_item ALTER COLUMN currency TYPE VARCHAR(3);
