ALTER TABLE events
    ADD COLUMN format VARCHAR(255) NOT NULL DEFAULT 'POOL_TO_ELIM';

-- events.status has no CHECK constraint but EventStatus enum constants are uppercase.
UPDATE events SET status = UPPER(status);
