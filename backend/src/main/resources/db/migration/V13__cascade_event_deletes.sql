-- Event/tournament deletion needs to reach teams and matches too. Event.teams/Event.matches
-- (JPA collections) were removed for being dead code, but that also removed Hibernate's only
-- path to cascade-delete those rows - the FKs themselves never had ON DELETE CASCADE. All six
-- FKs below are deferred (like bracket_slot_sources.bracket_match_id in V8) so Postgres doesn't
-- care which order it processes the cross-referencing pools/matches/teams cascades in.
ALTER TABLE teams DROP CONSTRAINT teams_event_id_fkey;
ALTER TABLE teams ADD CONSTRAINT teams_event_id_fkey
  FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE matches DROP CONSTRAINT matches_event_id_fkey;
ALTER TABLE matches ADD CONSTRAINT matches_event_id_fkey
  FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE matches DROP CONSTRAINT matches_pool_id_fkey;
ALTER TABLE matches ADD CONSTRAINT matches_pool_id_fkey
  FOREIGN KEY (pool_id) REFERENCES pools (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE matches DROP CONSTRAINT matches_team1_id_fkey;
ALTER TABLE matches ADD CONSTRAINT matches_team1_id_fkey
  FOREIGN KEY (team1_id) REFERENCES teams (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE matches DROP CONSTRAINT matches_team2_id_fkey;
ALTER TABLE matches ADD CONSTRAINT matches_team2_id_fkey
  FOREIGN KEY (team2_id) REFERENCES teams (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE matches DROP CONSTRAINT matches_winner_id_fkey;
ALTER TABLE matches ADD CONSTRAINT matches_winner_id_fkey
  FOREIGN KEY (winner_id) REFERENCES teams (id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

-- PoolEntry.seed was never read or written anywhere in the codebase.
ALTER TABLE pool_entries DROP COLUMN seed;
