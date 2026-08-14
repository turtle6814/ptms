ALTER TABLE matches ADD COLUMN bracket_type VARCHAR(16);
UPDATE matches SET bracket_type = 'WINNERS' WHERE match_type = 'BRACKET';

ALTER TABLE matches DROP CONSTRAINT match_shape;
ALTER TABLE matches ADD CONSTRAINT match_shape CHECK (
     (match_type = 'POOL'    AND pool_id IS NOT NULL AND round_number IS NOT NULL
        AND bracket_round IS NULL AND bracket_type IS NULL)
  OR (match_type = 'BRACKET' AND pool_id IS NULL AND bracket_round IS NOT NULL
        AND bracket_position IS NOT NULL AND bracket_type IN ('WINNERS', 'LOSERS'))
);

-- Series A/B adds a second bracket (LOSERS) whose rounds/positions restart at 1, same as the
-- WINNERS bracket - the old index collides across bracket types, so bracket_type joins the key.
DROP INDEX uq_bracket_slot;
CREATE UNIQUE INDEX uq_bracket_slot ON matches (event_id, bracket_type, bracket_round, bracket_position)
    WHERE match_type = 'BRACKET';
