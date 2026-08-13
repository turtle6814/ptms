ALTER TABLE matches ADD COLUMN match_type VARCHAR(16) NOT NULL DEFAULT 'POOL';
ALTER TABLE matches ADD COLUMN round_number INT;
UPDATE matches SET match_type = 'BRACKET' WHERE pool_id IS NULL;
UPDATE matches SET round_number = bracket_round, bracket_round = NULL WHERE match_type = 'POOL';

ALTER TABLE matches ADD CONSTRAINT match_shape CHECK (
     (match_type = 'POOL'    AND pool_id IS NOT NULL AND round_number IS NOT NULL AND bracket_round IS NULL)
  OR (match_type = 'BRACKET' AND pool_id IS NULL AND bracket_round IS NOT NULL AND bracket_position IS NOT NULL)
);
CREATE UNIQUE INDEX uq_bracket_slot ON matches (event_id, bracket_round, bracket_position) WHERE match_type = 'BRACKET';
