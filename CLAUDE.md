# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

**PTMS** (Pickleball Tournament Management System) — a full-stack app for organizing tournaments: a tournament is the top-level owned container holding one or more events, each event has pools/teams with round-robin play, pool winners feed into an elimination bracket, and admins score matches live while spectators watch via a public link/QR code. See `PRD.md` for full functional requirements, data model, and page/route/API tables.

| Layer     | Stack                                                  |
|-----------|---------------------------------------------------------|
| Frontend  | React 19 + TypeScript, Vite, React Router 7             |
| Backend   | Spring Boot 4 (Java 21), Spring Security (JWT), JPA/Hibernate, PostgreSQL |
| Real-time | STOMP over raw WebSocket (`/ws`, no SockJS)             |
| Styling   | Vanilla CSS (dark theme, glassmorphism), no CSS framework |

The repo is two independent projects (`backend/`, `frontend/`) that get combined into a **single deployable jar**: the Docker build compiles the frontend and copies `frontend/dist` into `backend`'s `src/main/resources/static`, so in production Spring Boot serves both the API and the SPA from one process/port.

## Commands

### Backend (`backend/`)
```
./mvnw spring-boot:run              # run the API (port 8080, or $PORT)
./mvnw test                         # run all tests
./mvnw test -Dtest=ClassName#method # run a single test
./mvnw package -Dmaven.test.skip=true -B   # build jar without tests (used in Docker build)
```
Needs a local Postgres reachable via `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` (defaults to `localhost:5432/pickleball_tms`, user `postgres`). `docker-compose.yml` spins up just the `db` service for local dev.

**Schema reset required before first run on this branch:** the Tournament/Event table shapes changed incompatibly (FK direction flip, dropped now-unmapped `NOT NULL` columns), and `ddl-auto=update` cannot migrate an existing pre-swap database. Drop and recreate the schema (`DROP SCHEMA public CASCADE; CREATE SCHEMA public;` against the target Postgres) before running against a database that predates this change — this includes a fresh local dev DB and the Render-managed Postgres.

### Frontend (`frontend/`)
```
npm run dev       # Vite dev server (port 5173)
npm run build     # tsc -b && vite build
npm run lint      # eslint .
npm run preview   # preview production build
```
Dev API/WS targets come from `.env.development` (`VITE_API_URL`, `VITE_WS_URL`, pointing at `http://localhost:8080`). No test runner is configured for the frontend.

### Full stack via Docker
```
docker compose up          # Postgres + full app (multi-stage build: frontend build -> backend build -> JRE runtime)
```
`Dockerfile` (repo root) builds both projects into one image; `backend/Dockerfile` is a backend-only build. Deployed to Render via `render.yaml` (single `docker` runtime web service + managed Postgres).

## Architecture

### Request routing / single-origin deployment
All API endpoints live under `/api/v1/**` (`AuthController` → `/api/v1/auth`, `EventController` → `/api/v1/events`, `TournamentController` → `/api/v1/tournaments`, `MatchController` → `/api/v1/events/{eventId}/matches`). Everything else (`/`, `/login`, `/admin/**`, `/tournaments/**`, `/view/**`, etc.) is forwarded to `index.html` by `SpaForwardingController` so React Router can take over client-side routing in the single-jar deployment. `SecurityConfig` has to permit both the API's public GETs *and* these SPA paths/static assets — when adding a new frontend route, add it to both `SpaForwardingController`'s mapping and `SecurityConfig`'s permit list, mirroring the existing entries.

### Auth
JWT-based, stateless (`SessionCreationPolicy.STATELESS`). `AuthTokenFilter` runs before `UsernamePasswordAuthenticationFilter` and validates the Bearer token via `JwtUtils`. Frontend stores the token in `localStorage` under `pickleball_auth_token` and injects it via an axios request interceptor (`frontend/src/api/client.ts`). `AuthContext`/`useAuth` hydrate user state from `GET /auth/me` on app mount using the stored token.

Public (unauthenticated) reads are deliberately narrow: only single-resource GETs (`/tournaments/{id}`, `/tournaments/{id}/events`, `/events/{id}`) are open, list endpoints are not — this is what backs the public tournament-viewer page (`/view/tournament/:tournamentId`) without exposing the admin's full tournament list.

### Tournament domain logic (the core complexity)
`TournamentServiceImpl` is a thin CRUD class for the top-level `Tournament` container. The real domain logic lives in `EventServiceImpl` and `MatchServiceImpl` (`backend/src/main/java/com/example/backend/service/impl/`):

- **Round-robin scheduling**: pools are scheduled with the Berger table / circle method (`EventServiceImpl.generateRoundRobinMatches`), including bye handling for odd team counts.
- **Elimination bracket generation**: `EventServiceImpl.generateEliminationBracket` creates placeholder matches (round/position, no teams yet) sized from the pool count — a 3rd-place match is added whenever there's a semifinal round. Pools are always sorted by name for stable seeding order.
- **Score submission cascade** (`MatchServiceImpl.updateScore`): recalculates pool standings from scratch (not incrementally) on every score update to avoid drift, then, depending on match type, either checks for pool completion and seeds the bracket (`checkAndAdvancePoolWinners`) or advances the bracket winner to the next round (`advanceInBracket`, including recursive bye auto-advancement and populating the 3rd-place match from semifinal losers). Finally `updateEventStatus` transitions `pool_play -> elimination -> completed` on the `Event`.
- Bracket match placement math (which pool's seed 1/2 goes to which Round-1 match, next-match position = `(pos+1)/2`) is intentionally coupled to the sorted-pool-index and position-parity conventions established at generation time — changing one without the other breaks seeding.
### Real-time updates
`WebSocketConfig` enables a simple in-memory STOMP broker at `/topic` with app prefix `/app`, endpoint `/ws` (no SockJS). Frontend uses `@stomp/stompjs` to subscribe per-event and replaces event state wholesale on each message (no client-side bracket-math duplication). When broadcasting new state after a mutation, publish to `/topic/event/{id}` so both admin and public viewer pick it up.

### DTO/entity mapping
`ModelMapper` (bean in `AppConfig`) converts entities to DTOs; `EventServiceImpl.convertToDTO` does a lot of manual post-processing on top of the auto-mapping (sorting pools/teams/matches/standings deterministically, assembling `EliminationBracketDTO` by grouping matches into rounds and deriving champion/3rd-place from the max round). Any new event/pool/match field likely needs both a `ModelMapper`-mapped field and manual DTO wiring here.

### Frontend structure
`src/api/` — axios client + typed API functions (`index.ts`), and shared types (`types.ts`) that should mirror backend DTOs. `src/pages/` — one component per route (see `PRD.md` §5 for the route table). `src/context/AuthContext.tsx` + `useAuth.ts` — auth state. `src/components/` — shared UI (bracket view, pool standings, match card, QR share modal, tabs). Each page/component has a co-located `.css` file (no CSS modules/styled-components).
