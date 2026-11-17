ALTER TABLE orders ADD COLUMN reservation_protocol INTEGER NOT NULL DEFAULT 1
    CHECK (reservation_protocol IN (1, 2));
