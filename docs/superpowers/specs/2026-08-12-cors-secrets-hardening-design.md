# CORS PATCH Gap + Hardcoded Secrets — Design

## Context

A project-wide audit (architecture, workflow, roles, security) turned up two small, unrelated,
independently-shippable issues, picked as the first fix out of a longer findings list:

1. **`SecurityConfig.corsConfigurationSource()`'s `allowedMethods` list is missing `PATCH`.**
   The `PATCH /api/v1/events/{eventId}/matches/{matchId}/rules` endpoint (added in the recent
   scoring-rules work) will be silently blocked by the browser's CORS preflight the moment any
   cross-origin client calls it — currently latent, since the frontend defines
   `updateMatchRules()` in `api/index.ts` but no UI wires it up yet, but it's a real bug waiting
   for its first caller.
2. **Real secrets are hardcoded as fallback defaults in `application.properties`**, committed to
   git: `spring.datasource.password=${DB_PASSWORD:140903}` and
   `app.jwt-secret=${JWT_SECRET:daf66e01593f61a15b857cf433aae03a005812b31234e149036bcc8dee755dbb}`.
   Production is not exposed today — `render.yaml` supplies the real `DB_PASSWORD` from the
   managed database and `generateValue: true`s a real `JWT_SECRET` — but the fallback values are
   real (the local dev Postgres password), sitting in git history regardless of whether any code
   path currently uses them.

**Goal:** close both gaps with the smallest possible change, without touching git history (a full
history scrub was considered and explicitly rejected — destructive, force-push, three remotes
(`origin/main`/`develop`/`master`), not justified for a local-organizer tool's dev-only secret).

**Non-goals:** git history rewrite, changing `render.yaml` (prod secrets are already env-sourced
correctly), redesigning the rules-override endpoint's HTTP method, addressing the other audit
findings (`@Data` on entities, double-cascade ownership between `Event`/`Pool` over `Team`/`Match`,
stale `PRD.md`) — each is its own follow-up, not bundled here.

---

## Changes

### 1. `backend/src/main/java/com/example/backend/config/SecurityConfig.java`

`corsConfigurationSource()`'s `configuration.setAllowedMethods(...)` gains `"PATCH"`:

```java
configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
```

### 2. `backend/src/main/resources/application.properties`

Drop the fallback defaults on the two secret-bearing properties only — every other property
(`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `PORT`, `app.jwt-expiration-milliseconds`) keeps its
existing default, since those aren't secrets and removing them would only add local-dev friction
for no security benefit:

```properties
spring.datasource.password=${DB_PASSWORD}
...
app.jwt-secret=${JWT_SECRET}
```

Effect: a local run or deploy missing either env var now fails fast at Spring context startup
(clear `PropertyNotFoundException`-style error naming the missing key) instead of silently
authenticating against a database with a real password that's sitting in git history, or signing
JWTs with a secret anyone with repo access already has.

### 3. New `.env.example` (repo root)

Documents every env var the backend reads, with placeholder (not real) values:

```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=pickleball_tms
DB_USER=postgres
DB_PASSWORD=changeme
JWT_SECRET=generate-a-real-base64-secret-here
PORT=8080
```

Not loaded automatically (no `spring-dotenv` dependency added — YAGNI, `export` / IDE run-config /
direnv all already cover this and the project doesn't otherwise use dotenv tooling) — it's
documentation for whoever sets up a local environment, mirroring how `render.yaml` already
documents the same vars for prod.

### 4. `CLAUDE.md`

One line added to the backend Commands section noting `DB_PASSWORD` and `JWT_SECRET` have no
default and pointing at `.env.example`.

### 5. Rotate the burned local Postgres password (manual step, not a code change)

The current local dev Postgres password (`140903`) is real and already in git history; rotating it
is what makes leaving history untouched acceptable. Done via `ALTER USER postgres WITH PASSWORD
'<new>'` against the local instance, then setting `DB_PASSWORD` in the shell/IDE env used to run
the backend. **This step touches the user's local Postgres credential and will be confirmed before
execution**, same as any other local-environment change — not something to script silently.
`JWT_SECRET` needs no separate rotation step: dropping its default already makes the old
hardcoded value unusable (no code path reads it anymore) without needing to touch any live secret
store.

---

## Verification

1. `./mvnw compile` — confirms `application.properties` change doesn't break Spring's property
   binding when `DB_PASSWORD`/`JWT_SECRET` env vars are set in the shell before running.
2. Boot the app **without** `DB_PASSWORD`/`JWT_SECRET` set — confirm it fails fast with a clear
   error naming the missing property, instead of silently connecting with the old default.
3. Boot the app **with** both env vars set (new rotated Postgres password) — confirm normal
   startup, `./mvnw test` still green.
4. Frontend: confirm `PATCH` requests now pass CORS preflight — either wire a quick manual
   `fetch(..., {method: 'PATCH'})` from the browser console against a running dev server, or rely
   on this being exercised once a future UI actually calls `updateMatchRules()`.
5. `docker compose up` not required for this change — no Dockerfile/render.yaml edits.
