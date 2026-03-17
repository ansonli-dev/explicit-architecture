-- V2: Fix currency column type from CHAR(3) to VARCHAR(3)
-- Hibernate schema validation expects varchar; CHAR(3) maps to bpchar in PostgreSQL
ALTER TABLE book ALTER COLUMN currency TYPE VARCHAR(3);
