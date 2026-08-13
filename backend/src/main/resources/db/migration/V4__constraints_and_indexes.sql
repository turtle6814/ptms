ALTER TABLE tournaments ALTER COLUMN owner_id SET NOT NULL;

CREATE UNIQUE INDEX uq_pool_name ON pools (event_id, lower(name));
CREATE UNIQUE INDEX uq_team_name ON teams (event_id, lower(name));

CREATE INDEX ix_match_event ON matches (event_id);
CREATE INDEX ix_match_pool  ON matches (pool_id);
CREATE INDEX ix_pool_event  ON pools (event_id);
CREATE INDEX ix_event_tourn ON events (tournament_id);

ALTER TABLE matches ADD CONSTRAINT chk_distinct_teams
  CHECK (team1_id IS NULL OR team2_id IS NULL OR team1_id <> team2_id);
ALTER TABLE matches ADD CONSTRAINT chk_scores_nonneg
  CHECK (COALESCE(team1_score,0) >= 0 AND COALESCE(team2_score,0) >= 0);
