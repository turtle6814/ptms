ALTER TABLE matches DROP CONSTRAINT match_shape;
ALTER TABLE matches ADD CONSTRAINT match_shape CHECK (
     (match_type = 'POOL'    AND pool_id IS NOT NULL AND round_number IS NOT NULL
        AND bracket_round IS NULL AND bracket_type IS NULL)
  OR (match_type = 'BRACKET' AND pool_id IS NULL AND bracket_round IS NOT NULL
        AND bracket_position IS NOT NULL AND bracket_type IN ('WINNERS', 'LOSERS', 'FINAL'))
);

-- Grand-final game 2 (reset) gets marked SKIPPED, no score, when the winners'-bracket champion
-- sweeps game 1 outright.
ALTER TABLE matches DROP CONSTRAINT matches_status_check;
ALTER TABLE matches ADD CONSTRAINT matches_status_check
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FORFEIT', 'WALKOVER', 'SKIPPED'));
