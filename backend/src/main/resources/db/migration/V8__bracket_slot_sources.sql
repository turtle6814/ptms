CREATE TABLE bracket_slot_sources (
  id UUID PRIMARY KEY,
  bracket_match_id UUID NOT NULL REFERENCES matches(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
  slot VARCHAR(8) NOT NULL,
  source_pool_id UUID NOT NULL REFERENCES pools(id) ON DELETE CASCADE,
  source_rank INT NOT NULL,
  UNIQUE (bracket_match_id, slot)
);
