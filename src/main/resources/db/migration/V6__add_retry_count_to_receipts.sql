-- V6: Track how many times a FAILED receipt has been retried.
-- retry_count = 0 → never retried (retry button shown)
-- retry_count = 1 → already retried once (retry button hidden)
ALTER TABLE receipts
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
