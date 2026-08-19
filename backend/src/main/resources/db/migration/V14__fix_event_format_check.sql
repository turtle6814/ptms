-- V10's chk_event_format used abbreviated format names ('POOL_TO_ELIM', 'POOL_TO_DOUBLE_ELIM')
-- that never matched the EventFormat Java enum (POOL_TO_ELIMINATION, POOL_TO_DOUBLE_ELIMINATION).
-- Existing rows with the abbreviated strings predate this mismatch and already fail to
-- deserialize via Hibernate (no matching enum constant) - backfill them before tightening
-- the constraint, or this migration itself would fail on those same rows.
ALTER TABLE events DROP CONSTRAINT chk_event_format;

UPDATE events SET format = 'POOL_TO_ELIMINATION' WHERE format = 'POOL_TO_ELIM';
UPDATE events SET format = 'POOL_TO_DOUBLE_ELIMINATION' WHERE format = 'POOL_TO_DOUBLE_ELIM';

ALTER TABLE events ADD CONSTRAINT chk_event_format CHECK (
    format IN ('POOL_TO_ELIMINATION', 'ROUND_ROBIN_ONLY', 'POOL_TO_DOUBLE_ELIMINATION', 'POOL_TO_SERIES_AB')
);
