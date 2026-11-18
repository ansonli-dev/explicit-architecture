UPDATE book
SET description = 'A practical guide to finding service boundaries and giving each model a shared language.'
WHERE id = '00000000-0000-4000-8000-000000000101'
  AND description IS NULL;
