-- D-1: Add optimistic locking version column to notification table.
-- Existing rows start at version 0; Hibernate increments on each UPDATE.
ALTER TABLE notification ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
