# Frontend Tournament/Event Swap — Design

## Context

The backend's `Tournament`/`Event` entity relationship was swapped (see `docs/superpowers/specs/2026-08-11-tournament-event-relationship-swap-design.md`, merged to `develop`): `Tournament` is now the top-level owned container, `Event` is the mandatory child holding pools/teams/matches/bracket — reversed from before. That spec explicitly deferred frontend work to a follow-up; this is that follow-up.

The frontend is currently completely broken against the new backend — every API call uses the old endpoint paths, old field names, and the old WebSocket topic.

**Goal:** Rename and reshape the frontend to match the new backend contract exactly — mechanical swap, not new features. Confirmed with the user: frontend naming (pages, routes, components) fully mirrors the backend rename, not just the data layer underneath, for the same reason the backend went with a full rename — permanently confusing names otherwise.

**Non-goals:** visual/CSS redesign beyond renamed labels, new tests (none exist to add to — no test runner is configured for the frontend per `CLAUDE.md`), fixing unrelated pre-existing frontend issues, new features.

---

## Naming & route mapping

Direct mirror of the backend swap:

| Old (pre-swap) | New (post-swap) |
|---|---|
| `EventsPage` (`/events`) — lists user's events | `TournamentsPage` (`/tournaments`) — lists user's tournaments |
| `EventDetailPage` (`/events/:eventId`) | `TournamentDetailPage` (`/tournaments/:tournamentId`) |
| `TournamentSetup` (`/setup?eventId=`) | `EventSetup` (`/setup?tournamentId=`) — `tournamentId` now **required**, not optional |
| `EventViewerPage` (`/view/event/:eventId`) | `TournamentViewerPage` (`/view/tournament/:tournamentId`) |
| `AdminDashboard` sidebar: Events → expandable Tournaments | Sidebar: Tournaments → expandable Events |

Component `EliminationBracket`/`MatchCard`/`PoolStandings`/`TournamentTabs`/`QRCodeShare`/`Header`/`ProtectedRoute` keep their names (they describe UI concepts, not the renamed entities) but their prop types/data shapes update to match.

---

## API layer (`api/types.ts` + `api/index.ts`)

Field/type swap mirrors the backend DTOs exactly:

```ts
// types.ts
interface Tournament {          // was Event
  id: string; name: string; description?: string | null;
  startDate?: string | null; endDate?: string | null;
  eventIds: string[];           // was tournamentIds
  createdAt: string; updatedAt: string;
}
interface Event {               // was Tournament
  id: string;
  tournamentId: string;         // was eventId, and was optional — now required
  name: string;
  status: 'setup' | 'pool_play' | 'elimination' | 'completed';
  teams: Team[]; pools: Pool[]; eliminationBracket?: EliminationBracket | null;
  createdAt: string; updatedAt: string;
}
// CreateTournamentRequest (was CreateEventRequest): name, description?, startDate?, endDate?
// CreateEventRequest (was CreateTournamentRequest): name, tournamentId: string (required), pools: PoolConfig[]
// UpdateTournamentRequest (renamed from UpdateEventRequest, unchanged shape) — no Update-Event equivalent, matches backend (Event has no PUT endpoint)
// Match/Pool/EliminationBracket: tournamentId field → eventId
```

`api/index.ts` functions rename in lockstep with the endpoints:
- `getAllEvents/getEventById/createEvent/updateEvent/deleteEvent/getEventTournaments` → `getAllTournaments/getTournamentById/createTournament/updateTournament/deleteTournament/getTournamentEvents` (hit `/tournaments`, `/tournaments/{id}`, `/tournaments/{id}/events`)
- `addTournamentToEvent`/`removeTournamentFromEvent` → **removed** (re-parenting endpoints don't exist on the new backend)
- `getAllTournaments/getTournament/createTournament/deleteTournament` → `getAllEvents/getEventById/createEvent/deleteEvent` (hit `/events`, `/events/{id}`; `createEvent`'s payload carries the now-required `tournamentId`)
- `updateMatchScore(tournamentId, update)` → `updateMatchScore(eventId, update)`, hitting `PUT /events/{eventId}/matches/{matchId}/score`, re-fetching via `getEventById`
- `subscribeTournament(tournamentId, cb)` → `subscribeEvent(eventId, cb)`, subscribing to `/topic/event/{eventId}` (was `/topic/tournament/{id}`)
- `generateShareableLink(tournamentId)` → returns `/view/tournament/${tournamentId}` (was `/view/event/${tournamentId}` — the old function's param was already misnamed relative to what it actually shared; this rename fixes it, not just relabels it)
- `pollTournament` → `pollEvent`

---

## Page-level flow changes

- **`TournamentsPage`** (was `EventsPage`): lists user's tournaments, "create tournament" modal is the simple name/description/dates form (was the "create event" modal). Navigates into `TournamentDetailPage` on create.
- **`TournamentDetailPage`** (was `EventDetailPage`): tournament header (inline-editable name/description/dates via `updateTournament`), lists its Events. "Add event" navigates to `/setup?tournamentId=...`. Per-event "remove" button is now a **real delete** (`deleteEvent`) — the old "remove tournament from event" was an unlink; that concept is gone since `Event.tournamentId` is mandatory now.
- **`EventSetup`** (was `TournamentSetup`): same pools/teams config UI, but `tournamentId` is now always present and required — no standalone entry point. Only ever reached from inside a `TournamentDetailPage`'s "Add event" button (a Tournament must exist before an Event can be created under it — decided during the backend brainstorming).
- **`AdminDashboard`**: sidebar lists Tournaments → expandable Events (`getTournamentEvents`). Selecting an event loads its pool/bracket detail (`getEventById`), scores post through `updateMatchScore(eventId, ...)`, WS via `subscribeEvent(eventId, ...)`, delete via `deleteEvent`. **Share button now shares the Tournament** (`/view/tournament/:tournamentId`), not the Event — the public dropdown-browse concept belongs at the top-container level, same as it did pre-swap (was share-the-Event, dropdown-browse-its-Tournaments).
- **`TournamentViewerPage`** (was `EventViewerPage`): public, no auth. Shows tournament name + dropdown of its Events (`getTournamentEvents`, public per `SecurityConfig`), selecting one loads pools/standings/bracket (`getEventById`, public). Live badge / WS / polling fallback unchanged in mechanism, retargeted to event-level data.
- **`EliminationBracket`/`PoolStandings`/`MatchCard`/`QRCodeShare`**: no structural changes — they render props; TypeScript catches the field renames (`Pool.tournamentId`→`eventId` etc.) at compile time.
- **`App.tsx`**: `/tournaments`, `/tournaments/:tournamentId`, `/view/tournament/:tournamentId` replace `/events`, `/events/:eventId`, `/view/event/:eventId`. `/setup` and `/admin` paths unchanged.

### Small backend addendum (in scope)

`SpaForwardingController.java` and `SecurityConfig.java` hardcode the SPA route list (currently `/events`, `/events/**`, no `/tournaments/**`) — `CLAUDE.md` itself documents "when adding a new frontend route, add it to both." Since this work adds `/tournaments/**` routes, that 2-line backend addition (mirroring the existing `/events` entries) is a necessary, in-scope part of this follow-up.

---

## Data flow, error handling, testing

**Data flow**: unchanged mechanism, retargeted scope — WS subscribe/broadcast and the optimistic-update logic in `bracketUpdateLogic.ts` operate per-Event now instead of per-Tournament (mirrors backend's `MatchController` broadcasting to `/topic/event/{id}`). Polling fallback in the viewer page stays as a fallback, unchanged.

**Error handling**: no changes — the existing `ApiResponse{success,data,error}` pattern and axios interceptor in `api/client.ts` are generic and untouched.

**Testing**: no test runner is configured for the frontend (confirmed in `CLAUDE.md`), so there's no existing suite to update. Verification is manual: `npm run dev`, click through the golden path (create tournament → create event → configure pools → score a match → watch bracket advance → public viewer shows live updates) plus a couple of edge cases.

---

## File-level rename pattern

Same two-file-swap style as the backend plan:

| Old file | New file (after swap) |
|---|---|
| `pages/EventsPage.tsx` + `.css` | `pages/TournamentsPage.tsx` + `.css` |
| `pages/EventDetailPage.tsx` + `.css` | `pages/TournamentDetailPage.tsx` + `.css` |
| `pages/TournamentSetup.tsx` + `.css` | `pages/EventSetup.tsx` + `.css` |
| `pages/EventViewerPage.tsx` + `.css` | `pages/TournamentViewerPage.tsx` + `.css` |
| `utils/tournamentLogic.ts` | `utils/eventLogic.ts` |
| `utils/bracketUpdateLogic.ts` | unchanged name (already event-scoped) |
| `api/types.ts`, `api/index.ts` | edited in place, not renamed |
| `components/*`, `context/*`, `App.tsx`, `Header.tsx` | edited in place — prop/type updates only, no renames |
| `pages/LandingPage.tsx` + `.css` | edited in place — copy-only (marketing text mentioning "events"/"tournaments"), no structural/prop changes, no rename |

`backend/.../SpaForwardingController.java`, `backend/.../SecurityConfig.java` — targeted 2-line additions (see addendum above), not full-file rewrites.
