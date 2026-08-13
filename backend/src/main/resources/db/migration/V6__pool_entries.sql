CREATE TABLE pool_entries (
  id UUID PRIMARY KEY,
  pool_id UUID NOT NULL REFERENCES pools(id) ON DELETE CASCADE,
  team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  seed INT,
  UNIQUE (pool_id, team_id)
);
INSERT INTO pool_entries (id, pool_id, team_id)
  SELECT gen_random_uuid(), pool_id, id FROM teams WHERE pool_id IS NOT NULL;
ALTER TABLE teams DROP COLUMN pool_id;
