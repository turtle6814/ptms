# CORS PATCH Gap + Secrets Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close two audit findings — a CORS preflight gap blocking the `PATCH` rules-override endpoint, and hardcoded real secrets committed as property defaults — with the smallest possible change and no git history rewrite.

**Architecture:** Three independent, sequentially-safe changes: (1) add `PATCH` to the existing CORS `allowedMethods` list, (2) rotate the local Postgres password and generate a fresh JWT secret entirely outside any committed file, (3) remove the two secret-bearing property defaults so a missing env var fails startup loudly instead of silently falling back, plus a `.env.example` and a `CLAUDE.md` pointer documenting the now-required vars.

**Tech Stack:** Spring Boot 4.0.2 / Java 21, Spring Security (CORS config), PostgreSQL, JUnit 5 + MockMvc.

## Global Constraints

- No git history rewrite (secret rotation makes the old leaked value inert; history is left alone).
- Do not modify `render.yaml` — prod secrets are already env-sourced correctly.
- Do not change the rules-override endpoint's HTTP method (stays `PATCH`) — fix CORS config, not the API.
- Only `DB_PASSWORD` and `JWT_SECRET` lose their property defaults. `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `PORT`, `app.jwt-expiration-milliseconds` keep theirs — they aren't secrets.
- No new dependency added for env-file loading (no `spring-dotenv`) — `.env.example` is documentation only.
- **Never write a real secret value into any file this plan or its execution produces** (including this plan itself, commit messages, or test code) — generate rotated values live in the terminal only.

---

### Task 1: Allow `PATCH` through CORS preflight

**Files:**
- Modify: `backend/src/main/java/com/example/backend/config/SecurityConfig.java:97`
- Test: `backend/src/test/java/com/example/backend/config/CorsConfigurationTest.java` (new)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: nothing later tasks depend on — this is a standalone config fix.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/example/backend/config/CorsConfigurationTest.java`:

```java
package com.example.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightAllowsPatchForCrossOriginRequests() throws Exception {
        mockMvc.perform(options(
                        "/api/v1/events/00000000-0000-0000-0000-000000000000/matches/00000000-0000-0000-0000-000000000000/rules")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("PATCH")));
    }
}
```

This mirrors the existing `@SpringBootTest`/`@AutoConfigureMockMvc` pattern already used in `BackendIntegrationTest.java` (same import path for `AutoConfigureMockMvc` — Spring Boot 4's reorganized package). The path doesn't need a real match/event to exist — a CORS preflight is handled before the request ever reaches a controller method.

- [ ] **Step 2: Run it, confirm it fails**

Run (from `backend/`, with `DB_PASSWORD`/`JWT_SECRET` exported to their current real local values so the context loads — Task 3 is what removes the defaults, not this task):

```bash
JAVA_HOME="C:\Program Files\Java\jdk-21.0.10" ./mvnw.cmd test -Dtest=CorsConfigurationTest
```

Expected: **FAIL** — `status().isOk()` fails because Spring's `DefaultCorsProcessor` rejects a preflight whose `Access-Control-Request-Method` isn't in the allowed list, responding `403`.

- [ ] **Step 3: Fix the CORS config**

In `backend/src/main/java/com/example/backend/config/SecurityConfig.java`, change line 97:

```java
// before
configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
// after
configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
```

- [ ] **Step 4: Run it again, confirm it passes**

```bash
JAVA_HOME="C:\Program Files\Java\jdk-21.0.10" ./mvnw.cmd test -Dtest=CorsConfigurationTest
```

Expected: **PASS**.

- [ ] **Step 5: Run the full suite to confirm no regression**

```bash
JAVA_HOME="C:\Program Files\Java\jdk-21.0.10" ./mvnw.cmd test
```

Expected: all tests pass (15 pre-existing + this new one = 16).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/config/SecurityConfig.java backend/src/test/java/com/example/backend/config/CorsConfigurationTest.java
git commit -m "fix: allow PATCH through CORS preflight for the match rules-override endpoint"
```

---

### Task 2: Rotate the local Postgres password and JWT secret

**No files are modified in this task.** This generates fresh credential values live in your terminal and applies them — nothing gets written to any file, committed, or pasted into chat/logs. This is what makes leaving git history untouched (per the design's rejected alternative #2) an acceptable tradeoff: the leaked value stops being valid.

**⚠️ Confirm before running the `ALTER USER` step below** — it changes your real local Postgres credential. If you'd rather do this step yourself, do so and skip to Task 3, using your own new password in place of `<new-password>` in every command there.

**Interfaces:**
- Consumes: nothing.
- Produces: two values held only in your shell session for the rest of this plan — referred to as `<new-password>` (Postgres) and `<new-jwt-secret>` (JWT signing key). Task 3's verification steps assume these are already exported as `DB_PASSWORD` / `JWT_SECRET` in the same shell you run `mvnw` from.

- [ ] **Step 1: Generate a new Postgres password and a new JWT secret**

```bash
openssl rand -base64 24   # -> use this as <new-password>
openssl rand -base64 48   # -> use this as <new-jwt-secret> (matches the ~48-byte length of the current key, safely long enough for HS384)
```

Copy both outputs somewhere only you can see them (password manager, or just keep the terminal scrollback for this session) — do not paste them into any file in this repo.

- [ ] **Step 2: Rotate the Postgres password**

```bash
"/c/Program Files/PostgreSQL/17/bin/psql.exe" -U postgres -h localhost -p 5432 -d pickleball_tms -c "ALTER USER postgres WITH PASSWORD '<new-password>';"
```

(Substitute your actual generated value for `<new-password>` — psql will prompt for the *current* password first, which is the old, now-being-retired one.)

- [ ] **Step 3: Export both values in the shell you'll run the backend/tests from**

```bash
export DB_PASSWORD='<new-password>'
export JWT_SECRET='<new-jwt-secret>'
```

These are per-shell-session only. Re-export them (or set them in your IDE run config / shell profile) any time you open a new terminal to run the backend, once Task 3 removes the fallback defaults.

- [ ] **Step 4: Verify the rotated password actually works**

```bash
PGPASSWORD='<new-password>' "/c/Program Files/PostgreSQL/17/bin/psql.exe" -U postgres -h localhost -p 5432 -d pickleball_tms -c "SELECT 1;"
```

Expected: returns `1`, no authentication error.

No commit for this task — nothing changed on disk.

---

### Task 3: Remove hardcoded secret defaults, add `.env.example`, document in `CLAUDE.md`

**Files:**
- Modify: `backend/src/main/resources/application.properties:7,26`
- Create: `.env.example` (repo root)
- Modify: `CLAUDE.md:27`

**Interfaces:**
- Consumes: `DB_PASSWORD` / `JWT_SECRET` already exported in your shell from Task 2, Step 3.
- Produces: nothing later tasks depend on.

- [ ] **Step 1: Confirm the app currently boots with your rotated values exported**

(Sanity check before making the defaults disappear — if this fails, fix Task 2 first.)

```bash
JAVA_HOME="C:\Program Files\Java\jdk-21.0.10" ./mvnw.cmd test
```

Expected: all 16 tests pass (using the exported `DB_PASSWORD`/`JWT_SECRET` from Task 2, not the old hardcoded defaults, which are still present in the file at this point but irrelevant since your env vars take precedence).

- [ ] **Step 2: Remove the two secret defaults**

In `backend/src/main/resources/application.properties`, line 7:

```properties
# before
spring.datasource.password=${DB_PASSWORD:140903}
# after
spring.datasource.password=${DB_PASSWORD}
```

Line 26:

```properties
# before
app.jwt-secret=${JWT_SECRET:daf66e01593f61a15b857cf433aae03a005812b31234e149036bcc8dee755dbb}
# after
app.jwt-secret=${JWT_SECRET}
```

- [ ] **Step 3: Verify it now fails fast without the env vars**

In a **fresh shell** where `DB_PASSWORD`/`JWT_SECRET` are NOT exported:

```bash
JAVA_HOME="C:\Program Files\Java\jdk-21.0.10" ./mvnw.cmd test
```

Expected: **BUILD FAILURE** — Spring context fails to start, error names the missing placeholder (`Could not resolve placeholder 'DB_PASSWORD'` or `'JWT_SECRET'`). This is the entire point of the change: no more silent fallback to a value that's sitting in git history.

- [ ] **Step 4: Verify it boots clean with the env vars set**

Back in the shell from Task 2 (where `DB_PASSWORD`/`JWT_SECRET` are exported to the rotated values):

```bash
JAVA_HOME="C:\Program Files\Java\jdk-21.0.10" ./mvnw.cmd test
```

Expected: all 16 tests pass.

- [ ] **Step 5: Create `.env.example`**

Create `.env.example` at the repo root:

```
DB_HOST=localhost
DB_PORT=5432
DB_NAME=pickleball_tms
DB_USER=postgres
DB_PASSWORD=changeme
JWT_SECRET=generate-a-real-base64-secret-here
PORT=8080
```

Every value is a placeholder — never a real credential. Mirrors the same var names `render.yaml` already wires up for prod.

- [ ] **Step 6: Update `CLAUDE.md`**

In `CLAUDE.md`, the backend Commands section currently reads (line 27):

```markdown
Needs a local Postgres reachable via `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` (defaults to `localhost:5432/pickleball_tms`, user `postgres`). `docker-compose.yml` spins up just the `db` service for local dev.
```

Change to:

```markdown
Needs a local Postgres reachable via `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` (host/port/db/user default to `localhost:5432/pickleball_tms`/`postgres`; `DB_PASSWORD` and `JWT_SECRET` have no default and must be set — see `.env.example`). `docker-compose.yml` spins up just the `db` service for local dev.
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/resources/application.properties .env.example CLAUDE.md
git commit -m "fix: remove hardcoded secret defaults, require DB_PASSWORD/JWT_SECRET via env"
```

---

## Self-Review

**Spec coverage:** design's 5 numbered changes all map to a task — (1) CORS → Task 1, (2) property defaults → Task 3, (3) `.env.example` → Task 3, (4) `CLAUDE.md` → Task 3, (5) password rotation → Task 2. Verification steps 1-3 from the spec are covered (compile/test with and without env vars); verification step 4 (frontend PATCH check) is covered by Task 1's `CorsConfigurationTest` at the backend level, which is the actual mechanism being fixed — no frontend code changes exist to test. Verification step 5 (no Docker needed) — correctly, no task touches Docker/render files.

**Placeholder scan:** no TBD/TODO; every step has literal commands or literal code.

**Type consistency:** N/A — no cross-task function signatures introduced (Task 1's test is self-contained; Task 2 produces shell-session values, not code symbols; Task 3 depends on those values by name only, matching `DB_PASSWORD`/`JWT_SECRET` consistently across all three tasks and the existing `application.properties` key names).
