# Tournament/Event Relationship Swap — Design

## Context

While fixing 5 critical findings from a backend security/entity audit (see `read-through-backend-folder-wiggly-cerf.md`), item #2 (add auth/ownership checks to `TournamentController`/`MatchController`) hit a blocker: the ownership chain `Match → Tournament → Event → User.owner` breaks whenever `Tournament.event` is null — a real, PRD-supported case (a tournament can be created standalone, then linked to an event later).

Investigating that blocker surfaced that the user's actual intended data model is the reverse of what's built: **`Tournament` should be the top-level container, `Event` should be the child competition unit that cannot exist without a tournament** — not "Event groups multiple Tournaments" as `PRD.md` and the current entities describe today.

This is scoped as its own backend-only sub-project. A frontend follow-up spec will land after this one — the app will not run end-to-end during the gap, which is accepted.

**Goal:** Swap the `Tournament`/`Event` entity roles so `Tournament` is the top-level, owned container and `Event` is the mandatory child holding the actual competition data (pools/teams/matches/bracket). As a direct consequence, this also resolves the three 🔴 Critical authorization findings from the audit (items #2 and #3), since the new mandatory FK makes the ownership chain always resolvable.

**Non-goals:** frontend changes (separate follow-up spec), RBAC/roles (separate 🟠 finding, out of scope), preserving existing DB data (schema reset is acceptable — confirmed with user), the other 3 critical audit findings (#1 secrets, #4 `@Data` on entities, #5 cascade ownership — handled separately, not part of this spec).

---

## Entity model

### New `Tournament` (top container) — was `Event.java`'s content, unchanged

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `name` | `String` | required |
| `description` | `String` | optional |
| `startDate` | `LocalDate` | optional |
| `endDate` | `LocalDate` | optional |
| `owner` | `User` (`@ManyToOne`, `JoinColumn("owner_id")`) | unchanged from today's `Event.owner` |
| `events` | `List<Event>` (`@OneToMany(mappedBy="tournament", cascade=ALL, orphanRemoval=true)`) | was `tournaments` |
| `createdAt`/`updatedAt` | `LocalDateTime` | unchanged |

### New `Event` (competition unit) — was `Tournament.java`'s content

| Field | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `name` | `String` | required |
| `status` | `EventStatus` (top-level enum, own file — see `enums/EventStatus.java`) | extracted from today's nested `Tournament.Status` |
| `tournament` | `Tournament` (`@ManyToOne`, `@JoinColumn(name="tournament_id", nullable=false)`) | **was nullable `event_id` — now required.** This is the change that makes the ownership chain always resolvable. |
| `teams` / `pools` / `matches` | unchanged shape, unchanged cascade | |
| `createdAt`/`updatedAt` | unchanged | |

Today's `Tournament.Status` is a nested enum inside `Tournament.java`. After the swap it becomes a standalone top-level enum class, `EventStatus` (values unchanged: `setup, pool_play, elimination, completed`), in a new top-level package `com.example.backend.enums` (file `enums/EventStatus.java`) — a sibling of `entity`/`service`/`controller`, not nested under `entity/`. (Package is `enums`, plural — `enum` is a reserved Java keyword and can't be used as a package name.) No value/semantic change, just extraction out of the entity class.

### `Pool` / `Team` / `Match` / `PoolStanding`

Their `tournament` field renames to `event` (`@ManyToOne @JoinColumn(name="event_id")`) — same shape, new label, since their direct parent is now the entity called `Event`. No other structural change to these four entities in this spec (cascade-ownership ambiguity between them is critical finding #5, handled separately).

For consistency with the `EventStatus` extraction, `Match.status` is also extracted: today's nested `Match.Status` (`pending, in_progress, completed`) becomes top-level `MatchStatus` (`enums/MatchStatus.java`), same package, same reasoning, no value change. `Match` itself isn't part of the swap — this is purely pulling its existing enum out of the class.

---

## File-level plan (backend only)

Two-file-swap pattern throughout — content of each pair trades places, requiring a temp name mid-rewrite to avoid collision:

| Old file | New file (after swap) |
|---|---|
| `entity/Event.java` | `entity/Tournament.java` |
| `entity/Tournament.java` | `entity/Event.java` |
| *(new file)* | `enums/EventStatus.java` (top-level enum, package `com.example.backend.enums`, extracted from today's nested `Tournament.Status`) |
| *(new file)* | `enums/MatchStatus.java` (top-level enum, same package, extracted from today's nested `Match.Status`) |
| `repository/EventRepository.java` | `repository/TournamentRepository.java` |
| `repository/TournamentRepository.java` | `repository/EventRepository.java` |
| `service/EventService.java` + `impl/EventServiceImpl.java` | `service/TournamentService.java` + `impl/TournamentServiceImpl.java` |
| `service/TournamentService.java` + `impl/TournamentServiceImpl.java` | `service/EventService.java` + `impl/EventServiceImpl.java` |
| `controller/EventController.java` | `controller/TournamentController.java` |
| `controller/TournamentController.java` | `controller/EventController.java` |
| `dto/EventDTO.java` | `dto/TournamentDTO.java` |
| `dto/TournamentDTO.java` | `dto/EventDTO.java` |
| `dto/CreateEventRequest.java` | `dto/CreateTournamentRequest.java` |
| `dto/CreateTournamentRequest.java` | `dto/CreateEventRequest.java` |
| `dto/UpdateEventRequest.java` | `dto/UpdateTournamentRequest.java` (no new-Event equivalent needed — new `Event` has no update endpoint, same as today's `Tournament`) |

`MatchController.java`, `MatchService.java`, `impl/MatchServiceImpl.java` keep their names (Match isn't part of the swap) but their internals change: URL path, DTO field references, and the new ownership check.

### Repository query renames

- `PoolRepository.findByTournamentId` → `findByEventId` (Pool's direct parent is now Event)
- `TeamRepository.findByTournamentId` → `findByEventId` (keep `findByPoolId` as-is)
- `MatchRepository.findByTournamentId` → `findByEventId` (keep `findByPoolId` as-is)
- `PoolStandingRepository` — unchanged (`findByPoolId`, `findByPoolIdAndTeamId` don't reference Tournament/Event directly)
- Old `EventRepository.findByOwner(User)` carries over verbatim as new `TournamentRepository.findByOwner(User)`
- Old `TournamentRepository.findByEventId(UUID)` becomes new `EventRepository.findByTournamentId(UUID)`

---

## API surface

### URL paths (follow the entity rename directly)

- `/api/v1/tournaments/**` → new `TournamentController` (top container CRUD)
- `/api/v1/events/**` → new `EventController` (competition-unit CRUD)
- `/api/v1/events/{eventId}/matches/{matchId}/score` → `MatchController` (was `/api/v1/tournaments/{tournamentId}/matches/...`; Match's direct parent is now Event)

### `SecurityConfig.java` permit-list must be updated to the new paths

`SecurityConfig.java`'s `filterChain` bean hardcodes the currently-public paths by literal string. These don't get renamed automatically just because the controllers do — this file needs a targeted edit (not part of the file-swap table above, it's a single file with matcher lines to update):

| Old matcher (remove) | New matcher (add) |
|---|---|
| `GET /api/v1/events/{id}` | `GET /api/v1/tournaments/{id}` |
| `GET /api/v1/events/{id}/tournaments` | `GET /api/v1/tournaments/{id}/events` |
| `GET /api/v1/tournaments/{id}` | `GET /api/v1/events/{id}` |

Without this edit, the public viewer 403s after the rename — the old permitted paths no longer match any real request, and the new controller paths default to `anyRequest().authenticated()`.

### Re-parenting endpoints are deleted, not migrated

Today's `EventController.addTournamentToEvent`/`removeTournamentFromEvent` exist only because `Tournament.event` was optional (a tournament could float unattached, then get linked). With `Event.tournament` now required, that concept doesn't exist: an Event is created directly under a Tournament via a required `tournamentId` on `CreateEventRequest`, and removing an Event is a real delete, not an unlink. These two endpoints and their service methods are removed entirely — no replacement needed.

### New `TournamentService` interface (top-level CRUD)

```java
public interface TournamentService {
    List<TournamentDTO> getAllTournaments(String username);
    TournamentDTO getTournamentById(UUID id);
    TournamentDTO createTournament(CreateTournamentRequest request, String username);
    TournamentDTO updateTournament(UUID id, UpdateTournamentRequest request, String username);
    void deleteTournament(UUID id, String username);
    List<EventDTO> getEvents(UUID tournamentId);
}
```
**Ownership split, corrected during plan-writing against `SecurityConfig.java`'s actual `permitAll()` list:** `SecurityConfig` already marks `GET /api/v1/tournaments/{id}` and `GET /api/v1/events/{id}/tournaments`-equivalent reads public, backing the PRD's unauthenticated QR-code viewer (`/view/event/:eventId`, PRD VW-01). So only `getAllTournaments`/`createTournament`/`updateTournament`/`deleteTournament` take `username` and enforce ownership via the existing `verifyOwnership(Tournament, username)` pattern (carried over from today's `EventServiceImpl`, unchanged). `getTournamentById`/`getEvents` take **no** `username` and do **no** ownership check — they stay exactly as public as they are today, so the QR-code share flow keeps working.

### New `EventService` interface (competition-unit CRUD)

```java
public interface EventService {
    List<EventDTO> getAllEvents(String username); // owner-scoped: only events under tournaments the caller owns
    EventDTO getEventById(UUID id); // public, no ownership check — backs the QR-code viewer (PRD VW-01)
    EventDTO createEvent(CreateEventRequest request, String username); // reads request.getTournamentId()
    void deleteEvent(UUID id, String username);
}
```
Ownership check (on `getAllEvents`/`createEvent`/`deleteEvent` only): `event.getTournament().getOwner().getUsername().equals(username)` — a new `verifyOwnership(Event, username)` helper that delegates to the tournament-level check. Always resolvable since `Event.tournament` is non-null. `getEventById` stays public, matching today's `GET /api/v1/tournaments/{id}` behavior (renamed path, same public-read intent).

### `MatchService.updateScore`

Gains a `String username` parameter; ownership check via `match.getEvent().getTournament().getOwner()`. (Score *updates* require the owner — only *reads* are public.)

### DTO field renames

- New `EventDTO.tournamentId` (was `TournamentDTO.eventId`) — **non-nullable**
- New `TournamentDTO.eventIds` (was `EventDTO.tournamentIds`)
- New `CreateEventRequest.tournamentId` (was `CreateTournamentRequest.eventId`) — **required, not optional**
- New `CreateTournamentRequest` — unchanged shape (was `CreateEventRequest`: `name`, `description`, `startDate`, `endDate`)

---

## Why this fixes the 3 original critical auth findings

This spec exists because fixing those findings hit a wall on the nullable FK — resolving that wall is the actual payoff:

- **Audit #2** (`TournamentController`/`MatchController` had zero authorization): new `TournamentService`/`EventService`/`MatchService` methods all take `username` and enforce ownership, following the exact `verifyOwnership` pattern already proven in today's `EventServiceImpl`.
- **Audit #3** (IDOR on `getEventById`/`getTournaments`): new `TournamentService.getTournamentById`/`getEvents` both check ownership — no more auth-less methods.
- Bonus: new `EventService.getAllEvents` becomes owner-scoped, closing the "any tournament in the system, to anyone" gap flagged as improvement #11 in the audit.

---

## Tests

`BackendIntegrationTest.java` currently exercises create-event-then-create-tournament in that order with static shared state across `@Order`ed tests. It will not compile against the renamed entities regardless, so updating its flow to create-tournament-then-create-event (using the new DTOs/paths) is a required part of this change, not scope creep. No new test *coverage* is being added here — untested cascade-delete and bracket-generation logic (audit's Missing Tests section) stays as-is, out of scope for this spec.

---

## Migration

Schema reset is acceptable — no existing data to preserve (confirmed with user). `ddl-auto=update` (or a manual drop/recreate if it doesn't cleanly handle the FK direction/nullability flip) rebuilds the schema fresh; no migration script needed.

**Confirmed during Task 10/11 execution:** `ddl-auto=update` did NOT cleanly handle the FK direction/nullability flip (it can't flip a foreign-key direction or drop now-unmapped `NOT NULL` columns, e.g. old `tournaments.status`, old `pools/teams/matches.tournament_id`). A manual drop/recreate (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`) against the local dev Postgres was required to get a working schema. This step was not scripted or committed anywhere — any other environment running this branch against a pre-swap database (a fresh clone's local Postgres, or the Render-managed Postgres) needs the same manual reset before first run. See `CLAUDE.md`'s backend Commands section for the documented instruction.
