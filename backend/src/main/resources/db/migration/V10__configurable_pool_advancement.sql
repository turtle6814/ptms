ALTER TABLE events
    ADD COLUMN advancement_per_pool INT NOT NULL DEFAULT 2,
    ADD COLUMN wildcard_count INT NOT NULL DEFAULT 0;

-- events.format had no CHECK constraint at all; add one now that a third value is on the way.
ALTER TABLE events ADD CONSTRAINT chk_event_format CHECK (
    format IN ('POOL_TO_ELIM', 'ROUND_ROBIN_ONLY', 'POOL_TO_DOUBLE_ELIM', 'POOL_TO_SERIES_AB')
);

ALTER TABLE bracket_slot_sources
    ALTER COLUMN source_pool_id DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'POOL_RANK',
    ADD COLUMN wildcard_rank INT;

ALTER TABLE bracket_slot_sources ADD CONSTRAINT chk_slot_source_shape CHECK (
    (source_type = 'POOL_RANK' AND source_pool_id IS NOT NULL AND wildcard_rank IS NULL)
 OR (source_type = 'WILDCARD'  AND source_pool_id IS NULL     AND wildcard_rank IS NOT NULL)
);
