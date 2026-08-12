ALTER TABLE matches
    ADD COLUMN target_score INTEGER NOT NULL DEFAULT 11,
    ADD COLUMN win_by_two BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN score_cap INTEGER NOT NULL DEFAULT 15;

-- V1's baseline missed a Hibernate-era CHECK constraint on status (created back when
-- ddl-auto=update managed the schema) that still only allows the original 3 values.
-- Widen it to also allow the new forfeit/walkover statuses, and uppercase everything
-- to match the MatchStatus/EventStatus enum constants.
ALTER TABLE matches DROP CONSTRAINT IF EXISTS matches_status_check;
UPDATE matches SET status = UPPER(status);
ALTER TABLE matches ADD CONSTRAINT matches_status_check
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FORFEIT', 'WALKOVER'));
