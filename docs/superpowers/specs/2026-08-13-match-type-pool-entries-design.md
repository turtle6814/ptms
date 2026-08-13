# Explicit match_type discriminator + pool_entries join table (ERD.md P3 + P4)

## Context

`ERD.md` documents the PTMS schema's structural problems (C1-C10) and a prioritized fix list
(P1-P11). Prior work in this branch has already landed P8 (constraints/indexes,
`V4__constraints_and_indexes.sql`) and a suite of characterization tests
(`BracketAdvancementTest`) pinning down the current bracket generation/seeding/advancement
behavior — the step the ERD's own order-of-work calls out as a prerequisite before touching that
code again.

This spec covers the next step: **P3** (explicit `match_type` discriminator, fixing C1 — "matches
is two entities in one table, discriminated by `pool_id IS NULL`") and **P4** (`pool_entries` join
table, fixing C4 — "`teams.event_id` and `teams.pool_id` are independent FKs", meaning a team can
silently be pointed at another event's pool). Both are tagged MUST in the ERD and grouped as one
"mechanical, low risk" step, prerequisite plumbing for P1+P2 (winner/loser bracket edges), which
is out of scope here.

Confirmed via code search: the frontend never reads `Team.poolId` or branches on `Match.poolId`/
`bracketRound` for pool matches — pool membership and match round grouping are both consumed via
already-split server-side DTOs (`Pool.teamIds`, `Pool.matches` vs `EliminationBracket.rounds`).
So both changes are backend/schema-only; no frontend files are touched.

The ERD's own SQL for P3 (§3, "P3 — Explicit match discriminator") is written against the fully
proposed schema in §2, which already assumes P1's `bracket_group`/`bracket_slot` columns exist.
Since P1 is not being done here, the migration below is adapted to the current schema: it keeps
`bracket_position` (no rename) and omits the `bracket_group` predicate/index.

## Migrations

Two files, continuing the existing one-file-per-ERD-item convention
(`V2__matches_scoring_rules.sql`, `V3__events_format.sql`, `V4__constraints_and_indexes.sql`).

### V5__match_type_discriminator.sql (P3)

```sql
ALTER TABLE matches ADD COLUMN match_type VARCHAR(16) NOT NULL DEFAULT 'POOL';
ALTER TABLE matches ADD COLUMN round_number INT;
UPDATE matches SET match_type = 'BRACKET' WHERE pool_id IS NULL;
UPDATE matches SET round_number = bracket_round, bracket_round = NULL WHERE match_type = 'POOL';

ALTER TABLE matches ADD CONSTRAINT match_shape CHECK (
     (match_type = 'POOL'    AND pool_id IS NOT NULL AND round_number IS NOT NULL AND bracket_round IS NULL)
  OR (match_type = 'BRACKET' AND pool_id IS NULL AND bracket_round IS NOT NULL AND bracket_position IS NOT NULL)
);
CREATE UNIQUE INDEX uq_bracket_slot ON matches (event_id, bracket_round, bracket_position) WHERE match_type = 'BRACKET';
```

Every bracket match (placeholder or 3rd-place) already has `bracket_round`+`bracket_position` set
at creation time (`createPlaceholderMatch`, the 3rd-place match block in
`generateEliminationBracket`), so the `match_shape` CHECK holds immediately for every row created
after this migration — there's no code path that inserts a BRACKET match without both columns set.

### V6__pool_entries.sql (P4)

```sql
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
```

`gen_random_uuid()` is built into Postgres 13+ (confirmed running Postgres 17 locally); no
extension needed.

## Entity and code changes

### P3

- New `com.example.backend.enums.MatchType { POOL, BRACKET }`.
- `Match.java`: add `matchType` (`@Enumerated(STRING)`, not null) and `roundNumber` (nullable
  `Integer`) fields. `bracketRound`/`bracketPosition` stay as-is (already nullable), now populated
  only for BRACKET matches.
- `EventServiceImpl.generateRoundRobinMatches`: `match.setMatchType(MatchType.POOL);
  match.setRoundNumber(round + 1);` replaces the current `match.setBracketRound(round + 1)`.
- `EventServiceImpl.createPlaceholderMatch` and the 3rd-place match block in
  `generateEliminationBracket`: add `match.setMatchType(MatchType.BRACKET);` (bracketRound/
  bracketPosition assignment unchanged).
- Every place that currently infers "this is a bracket match" from `m.getPool() == null` switches
  to `m.getMatchType() == MatchType.BRACKET` — this is the actual fix for C1, replacing an
  implicit convention repeated at each call site with one explicit field:
  - `MatchServiceImpl.advanceTournamentState`'s dispatch
    (`if (match.getPool() != null) ... else if (match.getBracketRound() != null) ...` becomes
    `if (match.getMatchType() == MatchType.POOL) ... else if (match.getMatchType() == MatchType.BRACKET) ...`)
  - `MatchServiceImpl.checkAndAdvancePoolWinners`'s bracket-match lookup
  - `MatchServiceImpl.advanceInBracket`'s bracket-match lookup
  - `MatchServiceImpl.updateEventStatus`'s elimination-match lookup
  - `EventServiceImpl.convertToDTO`'s bracket-match filter for `EliminationBracketDTO` assembly
- `EventServiceImpl.convertToDTO`'s pool-match sort (`MatchDTO::getBracketRound`, now always null
  for pool matches) switches to `MatchDTO::getRoundNumber`.
- `MatchDTO` gains a `roundNumber` field (mirrors the entity; harmless if unused by the frontend
  today, consistent with how other match fields are exposed).

### P4

- New `PoolEntry` entity (`id`, `pool` FK, `team` FK, nullable `seed`) + `PoolEntryRepository`
  (`findByPoolId`).
- `Team.java`: remove the `pool` field and its `@JoinColumn`.
- `Pool.java`: remove the `teams` field; add `poolEntries`
  (`@OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true)`), mirroring
  the existing `matches`/`standings` collections on `Pool`.
- `TeamDTO`: remove `poolId` (per your answer — nothing reads it, and Team no longer has a single
  well-defined pool once modeled as a join table).
- Delete `TeamRepository.findByPoolId` — declared but never called today, and based on the FK
  column this migration removes.
- New private helper in `EventServiceImpl`:
  `private List<Team> teamsInPool(Pool pool) { return pool.getPoolEntries().stream().map(PoolEntry::getTeam).toList(); }`
  replacing the three `pool.getTeams()` call sites:
  - `EventServiceImpl.createEvent`: instead of `team.setPool(pool)`, build one `PoolEntry` per
    team (seed left null — the ERD scopes `seed` to future manual seeding, not used by the
    generator) and add it to `pool.getPoolEntries()` so it's cascade-persisted the same way pools/
    teams/matches already are today.
  - `EventServiceImpl.generateRoundRobinMatches`
  - `EventServiceImpl.initializeStandings`
  - `EventServiceImpl.convertToDTO`'s `PoolDTO.teamIds` assembly — teams still sorted by
    `Team::getCreatedAt` as today; only the source of "which teams are in this pool" changes, not
    the ordering.

## Testing

No new tests. `BackendIntegrationTest` and `BracketAdvancementTest` already exercise every code
path this change touches — event creation, pool completion, seeding, bracket advancement — without
asserting on the removed columns directly (they go through the API/DTOs). A green re-run of the
existing 21 tests is the direct regression signal the prior characterization-test step was built
to produce.

## Out of scope

P1 (winner/loser edges), P2 (declarative seeding via `bracket_slot_sources`), P6 (drop
`pool_standings`), P9 (`scored_by`), P10 (`share_links`), P11 (`tournament_members`) — all
unaffected by this change and deferred per the ERD's order-of-work.
