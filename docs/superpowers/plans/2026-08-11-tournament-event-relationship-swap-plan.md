# Tournament/Event Relationship Swap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Swap the `Tournament`/`Event` entity roles in `backend/` so `Tournament` is the top-level, owned container and `Event` is the mandatory child holding pools/teams/matches/bracket — fixing the nullable-FK ownership gap that blocked the original 3 critical auth findings, and extract `EventStatus`/`MatchStatus` into a new top-level `enums` package along the way.

**Architecture:** Mechanical two-file content swap (`Tournament.java`⟷`Event.java` and their repos/services/controllers/DTOs trade content) executed as direct full-file rewrites (not `git mv` — each target file is written with its final content directly via captured source, so there's no rename-collision step to sequence), plus new ownership checks on write operations, plus enum extraction. Because nearly every layer cross-references the swapped types, **intermediate tasks will not compile in isolation** — that's inherent to a mutual-reference rename, not a task-sizing failure. Each task is still independently reviewable (a reviewer can judge "is this file's mapping to its old counterpart correct?" without the whole project building), but the first real compile/test checkpoint is Task 11.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Spring Security, Lombok, ModelMapper.

## Global Constraints

- Backend only (`backend/src/main/java/com/example/backend/` and `backend/src/test/`). Frontend is a separate follow-up spec — do not touch `frontend/`.
- Schema reset is acceptable — no data migration script, no `git mv`/DB migration tooling needed.
- Do not add new test coverage beyond fixing `BackendIntegrationTest.java` to compile and pass under the new shape (explicitly out of scope per the spec).
- Do not touch the other 3 critical audit findings (#1 hardcoded secrets, #4 `@Data` on entities, #5 cascade-ownership ambiguity between `Tournament`/`Pool`) — handled separately.
- Single-resource GET-by-id endpoints (`getTournamentById`, `getEvents`-under-tournament, `getEventById`) stay public, no `username` parameter, no ownership check — this backs the PRD's unauthenticated QR-code viewer. Only list-all/create/update/delete/score-update take `username` and enforce ownership.
- Package for extracted enums is `com.example.backend.enums` (plural — `enum` is a reserved Java keyword), a new top-level sibling of `entity`/`service`/`controller`, not nested under `entity/`.

Full source-of-truth for every decision below: `docs/superpowers/specs/2026-08-11-tournament-event-relationship-swap-design.md`.

---

### Task 1: Extract `EventStatus` and `MatchStatus` enums

**Files:**
- Create: `backend/src/main/java/com/example/backend/enums/EventStatus.java`
- Create: `backend/src/main/java/com/example/backend/enums/MatchStatus.java`

**Interfaces:**
- Produces: `com.example.backend.enums.EventStatus` (values `setup, pool_play, elimination, completed`), `com.example.backend.enums.MatchStatus` (values `pending, in_progress, completed`) — consumed by Task 2 (`Event.java`, `Match.java`) and later service tasks.

- [ ] **Step 1: Create `EventStatus.java`**

```java
package com.example.backend.enums;

public enum EventStatus {
    setup, pool_play, elimination, completed
}
```

- [ ] **Step 2: Create `MatchStatus.java`**

```java
package com.example.backend.enums;

public enum MatchStatus {
    pending, in_progress, completed
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/example/backend/enums/
git commit -m "feat: extract EventStatus and MatchStatus into top-level enums package"
```

---

### Task 2: Rewrite entities (`Tournament`, `Event`, `Pool`, `Team`, `Match`)

These five files are mutually referential (bidirectional JPA associations) — they must be edited together as one unit; none compiles correctly in isolation from the others.

**Files:**
- Modify: `backend/src/main/java/com/example/backend/entity/Tournament.java` (full rewrite — becomes the top container, was `Event.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/entity/Event.java` (full rewrite — becomes the competition unit, was `Tournament.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/entity/Pool.java` (`tournament` field → `event`)
- Modify: `backend/src/main/java/com/example/backend/entity/Team.java` (`tournament` field → `event`)
- Modify: `backend/src/main/java/com/example/backend/entity/Match.java` (`tournament` field → `event`; `Status` → top-level `MatchStatus`)
- Not modified: `backend/src/main/java/com/example/backend/entity/PoolStanding.java`, `backend/src/main/java/com/example/backend/entity/User.java`

**Interfaces:**
- Consumes: `EventStatus`, `MatchStatus` from Task 1.
- Produces: `Tournament{id,name,description,startDate,endDate,owner,events,createdAt,updatedAt}`, `Event{id,name,status,tournament,teams,pools,matches,createdAt,updatedAt}` — consumed by every later task.

- [ ] **Step 1: Rewrite `Tournament.java`**

```java
package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tournaments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Event> events = new ArrayList<>();
}
```

- [ ] **Step 2: Rewrite `Event.java`**

```java
package com.example.backend.entity;

import com.example.backend.enums.EventStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.pool_play;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Team> teams = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pool> pools = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Match> matches = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Rewrite `Pool.java`**

```java
package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pools")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL)
    private List<Team> teams = new ArrayList<>();

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL)
    private List<Match> matches = new ArrayList<>();

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL)
    private List<PoolStanding> standings = new ArrayList<>();

    @Column(nullable = false)
    private boolean isComplete = false;
}
```

- [ ] **Step 4: Rewrite `Team.java`**

```java
package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "teams")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id")
    private Pool pool;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Rewrite `Match.java`**

```java
package com.example.backend.entity;

import com.example.backend.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id")
    private Pool pool;

    @Column(name = "bracket_round")
    private Integer bracketRound;

    @Column(name = "bracket_position")
    private Integer bracketPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team1_id")
    private Team team1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team2_id")
    private Team team2;

    private Integer team1Score;
    private Integer team2Score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Team winner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status = MatchStatus.pending;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/entity/
git commit -m "feat: swap Tournament/Event entity roles, rename Pool/Team/Match parent to Event"
```

---

### Task 3: Rewrite repositories

**Files:**
- Modify: `backend/src/main/java/com/example/backend/repository/TournamentRepository.java` (full rewrite — was `EventRepository.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/repository/EventRepository.java` (full rewrite — was `TournamentRepository.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/repository/PoolRepository.java`
- Modify: `backend/src/main/java/com/example/backend/repository/TeamRepository.java`
- Modify: `backend/src/main/java/com/example/backend/repository/MatchRepository.java`
- Not modified: `backend/src/main/java/com/example/backend/repository/PoolStandingRepository.java`, `backend/src/main/java/com/example/backend/repository/UserRepository.java`

**Interfaces:**
- Consumes: `Tournament`, `Event`, `Pool`, `Team`, `Match`, `User` from Task 2.
- Produces: `TournamentRepository.findByOwner(User)`, `EventRepository.findByTournamentId(UUID)`, `PoolRepository.findByEventId(UUID)`, `TeamRepository.findByEventId(UUID)`/`findByPoolId(UUID)`, `MatchRepository.findByEventId(UUID)`/`findByPoolId(UUID)` — consumed by Tasks 5–7.

- [ ] **Step 1: Rewrite `TournamentRepository.java`**

```java
package com.example.backend.repository;

import com.example.backend.entity.Tournament;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, UUID> {
    List<Tournament> findByOwner(User owner);
}
```

- [ ] **Step 2: Rewrite `EventRepository.java`**

```java
package com.example.backend.repository;

import com.example.backend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByTournamentId(UUID tournamentId);
}
```

- [ ] **Step 3: Rewrite `PoolRepository.java`**

```java
package com.example.backend.repository;

import com.example.backend.entity.Pool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PoolRepository extends JpaRepository<Pool, UUID> {
    List<Pool> findByEventId(UUID eventId);
}
```

- [ ] **Step 4: Rewrite `TeamRepository.java`**

```java
package com.example.backend.repository;

import com.example.backend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    List<Team> findByEventId(UUID eventId);

    List<Team> findByPoolId(UUID poolId);
}
```

- [ ] **Step 5: Rewrite `MatchRepository.java`**

```java
package com.example.backend.repository;

import com.example.backend.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {
    List<Match> findByEventId(UUID eventId);

    List<Match> findByPoolId(UUID poolId);
}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/example/backend/repository/
git commit -m "feat: swap Tournament/Event repository roles, rename child-repo tournamentId queries to eventId"
```

---

### Task 4: Rewrite DTOs

**Files:**
- Modify: `backend/src/main/java/com/example/backend/dto/TournamentDTO.java` (full rewrite — was `EventDTO.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/dto/EventDTO.java` (full rewrite — was `TournamentDTO.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/dto/CreateTournamentRequest.java` (full rewrite — was `CreateEventRequest.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/dto/CreateEventRequest.java` (full rewrite — was `CreateTournamentRequest.java`'s content, `tournamentId` now required)
- Create: `backend/src/main/java/com/example/backend/dto/UpdateTournamentRequest.java` (renamed from `UpdateEventRequest.java`)
- Delete: `backend/src/main/java/com/example/backend/dto/UpdateEventRequest.java`
- Modify: `backend/src/main/java/com/example/backend/dto/PoolDTO.java` (`tournamentId` → `eventId`)
- Modify: `backend/src/main/java/com/example/backend/dto/TeamDTO.java` (`tournamentId` → `eventId`)
- Modify: `backend/src/main/java/com/example/backend/dto/MatchDTO.java` (`tournamentId` → `eventId`)
- Modify: `backend/src/main/java/com/example/backend/dto/EliminationBracketDTO.java` (`tournamentId` → `eventId`)
- Not modified: `backend/src/main/java/com/example/backend/dto/PoolConfigDTO.java`, `PoolStandingDTO.java`, `BracketRoundDTO.java`, `ApiResponse.java`, `ScoreUpdateRequest.java`, `AuthDtos.java`, `UserDTO.java`

**Interfaces:**
- Produces: `TournamentDTO{id,name,description,startDate,endDate,eventIds,createdAt,updatedAt}`, `EventDTO{id,tournamentId,name,status,teams,pools,eliminationBracket,createdAt,updatedAt}`, `CreateTournamentRequest{name,description,startDate,endDate}`, `CreateEventRequest{name,tournamentId,pools}`, `UpdateTournamentRequest{name,description,startDate,endDate}` — consumed by Tasks 5–8.

- [ ] **Step 1: Rewrite `TournamentDTO.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TournamentDTO {
    private UUID id;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<UUID> eventIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Rewrite `EventDTO.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class EventDTO {
    private UUID id;
    private UUID tournamentId;
    private String name;
    private String status;
    private List<TeamDTO> teams;
    private List<PoolDTO> pools;
    private EliminationBracketDTO eliminationBracket;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Rewrite `CreateTournamentRequest.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class CreateTournamentRequest {
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
}
```

- [ ] **Step 4: Rewrite `CreateEventRequest.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateEventRequest {
    private String name;
    private UUID tournamentId;
    private List<PoolConfigDTO> pools;
}
```

- [ ] **Step 5: Create `UpdateTournamentRequest.java`, delete `UpdateEventRequest.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateTournamentRequest {
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
}
```

Delete `backend/src/main/java/com/example/backend/dto/UpdateEventRequest.java`.

- [ ] **Step 6: Rewrite `PoolDTO.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PoolDTO {
    private UUID id;
    private UUID eventId;
    private String name;
    private List<UUID> teamIds;
    private List<MatchDTO> matches;
    private List<PoolStandingDTO> standings;
    private boolean isComplete;
}
```

- [ ] **Step 7: Rewrite `TeamDTO.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class TeamDTO {
    private UUID id;
    private String name;
    private UUID eventId;
    private UUID poolId;
}
```

- [ ] **Step 8: Rewrite `MatchDTO.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MatchDTO {
    private UUID id;
    private UUID eventId;
    private UUID poolId;
    private Integer bracketRound;
    private Integer bracketPosition;
    private UUID team1Id;
    private UUID team2Id;
    private Integer team1Score;
    private Integer team2Score;
    private UUID winnerId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 9: Rewrite `EliminationBracketDTO.java`**

```java
package com.example.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class EliminationBracketDTO {
    private UUID eventId;
    private List<BracketRoundDTO> rounds;
    private UUID champion;
    private MatchDTO thirdPlaceMatch;
    private UUID thirdPlaceTeamId;
}
```

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/example/backend/dto/
git commit -m "feat: swap Tournament/Event DTO roles, rename tournamentId fields to eventId on child DTOs"
```

---

### Task 5: Rewrite `TournamentService`/`TournamentServiceImpl`

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/TournamentService.java` (full rewrite — was `EventService.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/service/impl/TournamentServiceImpl.java` (full rewrite — was `EventServiceImpl.java`'s content)

**Interfaces:**
- Consumes: `Tournament`, `Event` (Task 2); `TournamentRepository`, `EventRepository`, `UserRepository` (Task 3); `TournamentDTO`, `EventDTO`, `CreateTournamentRequest`, `UpdateTournamentRequest` (Task 4).
- Produces: `TournamentService.getAllTournaments(String username)`, `.getTournamentById(UUID id)` (public, no ownership check), `.createTournament(CreateTournamentRequest, String username)`, `.updateTournament(UUID id, UpdateTournamentRequest, String username)`, `.deleteTournament(UUID id, String username)`, `.getEvents(UUID tournamentId)` (public) — consumed by Task 8 (`TournamentController`).

- [ ] **Step 1: Rewrite `TournamentService.java`**

```java
package com.example.backend.service;

import com.example.backend.dto.*;

import java.util.List;
import java.util.UUID;

public interface TournamentService {
    List<TournamentDTO> getAllTournaments(String username);

    TournamentDTO getTournamentById(UUID id);

    TournamentDTO createTournament(CreateTournamentRequest request, String username);

    TournamentDTO updateTournament(UUID id, UpdateTournamentRequest request, String username);

    void deleteTournament(UUID id, String username);

    List<EventDTO> getEvents(UUID tournamentId);
}
```

- [ ] **Step 2: Rewrite `TournamentServiceImpl.java`**

```java
package com.example.backend.service.impl;

import com.example.backend.dto.*;
import com.example.backend.entity.Event;
import com.example.backend.entity.Tournament;
import com.example.backend.entity.User;
import com.example.backend.repository.EventRepository;
import com.example.backend.repository.TournamentRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<TournamentDTO> getAllTournaments(String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return tournamentRepository.findByOwner(owner).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public TournamentDTO getTournamentById(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        return convertToDTO(tournament);
    }

    @Override
    public TournamentDTO createTournament(CreateTournamentRequest request, String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Tournament tournament = modelMapper.map(request, Tournament.class);
        tournament.setOwner(owner);
        Tournament savedTournament = tournamentRepository.save(tournament);
        return convertToDTO(savedTournament);
    }

    @Override
    public TournamentDTO updateTournament(UUID id, UpdateTournamentRequest request, String username) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        verifyOwnership(tournament, username);

        if (request.getName() != null)
            tournament.setName(request.getName());
        if (request.getDescription() != null)
            tournament.setDescription(request.getDescription());
        if (request.getStartDate() != null)
            tournament.setStartDate(request.getStartDate());
        if (request.getEndDate() != null)
            tournament.setEndDate(request.getEndDate());

        Tournament updatedTournament = tournamentRepository.save(tournament);
        return convertToDTO(updatedTournament);
    }

    @Override
    public void deleteTournament(UUID id, String username) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        verifyOwnership(tournament, username);
        tournamentRepository.deleteById(id);
    }

    @Override
    public List<EventDTO> getEvents(UUID tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        return tournament.getEvents().stream()
                .map(e -> modelMapper.map(e, EventDTO.class))
                .collect(Collectors.toList());
    }

    private void verifyOwnership(Tournament tournament, String username) {
        if (tournament.getOwner() == null || !tournament.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to modify this tournament");
        }
    }

    private TournamentDTO convertToDTO(Tournament tournament) {
        TournamentDTO dto = modelMapper.map(tournament, TournamentDTO.class);
        if (tournament.getEvents() != null) {
            dto.setEventIds(tournament.getEvents().stream()
                    .map(Event::getId)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}
```

Note: today's `EventServiceImpl` has two more methods, `addTournamentToEvent`/`removeTournamentFromEvent` (with `@Transactional`) — these are the re-parenting endpoints the spec deletes (not migrated), so they're intentionally absent above.

- [ ] **Step 3: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: still fails (later tasks not done yet) — confirm the *only* errors reported are in files not yet touched (`MatchServiceImpl.java`, `EventController.java`, `TournamentController.java`, `MatchController.java`, `EventServiceImpl.java`/`TournamentServiceImpl.java` old content, `BackendIntegrationTest.java`), not in anything from Tasks 1–5.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/backend/service/TournamentService.java backend/src/main/java/com/example/backend/service/impl/TournamentServiceImpl.java
git commit -m "feat: swap TournamentService/Impl role, add ownership checks to write operations"
```

---

### Task 6: Rewrite `EventService`/`EventServiceImpl`

The largest task — this class inherits the round-robin scheduling and elimination-bracket generation logic (previously `TournamentServiceImpl`, ~366 lines), retyped from `Tournament` to `Event`.

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/EventService.java` (full rewrite — was `TournamentService.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/service/impl/EventServiceImpl.java` (full rewrite — was `TournamentServiceImpl.java`'s content)

**Interfaces:**
- Consumes: `Tournament`, `Event`, `Pool`, `Team`, `Match`, `PoolStanding` (Task 2); `TournamentRepository`, `EventRepository`, `PoolRepository`, `TeamRepository`, `MatchRepository`, `PoolStandingRepository`, `UserRepository` (Task 3); `EventDTO`, `CreateEventRequest`, `PoolConfigDTO`, `PoolDTO`, `MatchDTO`, `EliminationBracketDTO`, `BracketRoundDTO` (Task 4); `EventStatus`, `MatchStatus` (Task 1).
- Produces: `EventService.getAllEvents(String username)` (owner-scoped), `.getEventById(UUID id)` (public, no ownership check), `.createEvent(CreateEventRequest, String username)` (reads `request.getTournamentId()`), `.deleteEvent(UUID id, String username)` — consumed by Task 7 (`MatchServiceImpl`, via `EventRepository`) and Task 8 (`EventController`, `MatchController`).

- [ ] **Step 1: Rewrite `EventService.java`**

```java
package com.example.backend.service;

import com.example.backend.dto.CreateEventRequest;
import com.example.backend.dto.EventDTO;

import java.util.List;
import java.util.UUID;

public interface EventService {
    List<EventDTO> getAllEvents(String username);

    EventDTO getEventById(UUID id);

    EventDTO createEvent(CreateEventRequest request, String username);

    void deleteEvent(UUID id, String username);
}
```

- [ ] **Step 2: Rewrite `EventServiceImpl.java`**

```java
package com.example.backend.service.impl;

import com.example.backend.dto.*;
import com.example.backend.entity.*;
import com.example.backend.enums.EventStatus;
import com.example.backend.enums.MatchStatus;
import com.example.backend.repository.*;
import com.example.backend.service.EventService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final PoolRepository poolRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PoolStandingRepository poolStandingRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<EventDTO> getAllEvents(String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return tournamentRepository.findByOwner(owner).stream()
                .flatMap(tournament -> tournament.getEvents().stream())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EventDTO getEventById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        return convertToDTO(event);
    }

    @Override
    @Transactional
    public EventDTO createEvent(CreateEventRequest request, String username) {
        Tournament tournament = tournamentRepository.findById(request.getTournamentId())
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        verifyOwnership(tournament, username);

        Event event = new Event();
        event.setName(request.getName());
        event.setStatus(EventStatus.pool_play);
        event.setTournament(tournament);

        List<Pool> pools = new ArrayList<>();
        List<Team> allTeams = new ArrayList<>();
        List<Match> allMatches = new ArrayList<>();

        for (PoolConfigDTO poolConfig : request.getPools()) {
            Pool pool = new Pool();
            pool.setName(poolConfig.getName());
            pool.setEvent(event);

            List<Team> poolTeams = new ArrayList<>();
            for (String teamName : poolConfig.getTeamNames()) {
                Team team = new Team();
                team.setName(teamName);
                team.setEvent(event);
                team.setPool(pool);
                poolTeams.add(team);
                allTeams.add(team);
            }
            pool.setTeams(poolTeams);
            pools.add(pool);
        }

        event.setPools(pools);
        event.setTeams(allTeams);

        Event savedEvent = eventRepository.save(event);

        // Now generate matches and standings
        for (Pool pool : savedEvent.getPools()) {
            generateRoundRobinMatches(pool, savedEvent, allMatches);
            initializeStandings(pool);
        }

        // Generate placeholder elimination bracket (Semis and Finals)
        generateEliminationBracket(savedEvent, allMatches);

        savedEvent.setMatches(allMatches);
        eventRepository.save(savedEvent);

        return convertToDTO(savedEvent);
    }

    private void generateRoundRobinMatches(Pool pool, Event event, List<Match> allMatches) {
        List<Team> teams = new ArrayList<>(pool.getTeams());
        int n = teams.size();

        if (n < 2)
            return;

        // Berger Table (Circle Method)
        // If odd number of teams, add a dummy team
        if (n % 2 != 0) {
            teams.add(null); // Dummy team
            n++;
        }

        int rounds = n - 1;
        int matchesPerRound = n / 2;

        for (int round = 0; round < rounds; round++) {
            for (int matchIndex = 0; matchIndex < matchesPerRound; matchIndex++) {
                Team home = teams.get(matchIndex);
                Team away = teams.get(n - 1 - matchIndex);

                // If neither team is dummy, schedule match
                if (home != null && away != null) {
                    Match match = new Match();
                    match.setEvent(event);
                    match.setPool(pool);
                    match.setTeam1(home);
                    match.setTeam2(away);
                    match.setStatus(MatchStatus.pending);
                    match.setBracketRound(round + 1); // Store round number (1-based)

                    matchRepository.save(match);
                    allMatches.add(match);
                }
            }

            // Rotate teams: Keep index 0 fixed, rotate the rest clockwise
            Team last = teams.remove(teams.size() - 1);
            teams.add(1, last);
        }
    }

    private void initializeStandings(Pool pool) {
        for (Team team : pool.getTeams()) {
            PoolStanding standing = new PoolStanding();
            standing.setPool(pool);
            standing.setTeam(team);
            standing.setWins(0);
            standing.setLosses(0);
            standing.setPointsFor(0);
            standing.setPointsAgainst(0);
            standing.setPointDifferential(0);
            poolStandingRepository.save(standing);
        }
    }

    private void generateEliminationBracket(Event event, List<Match> allMatches) {
        int numPools = event.getPools().size();
        if (numPools < 1)
            return;

        List<Match> currentRoundMatches = new ArrayList<>();
        int roundNumber = 1;

        if (numPools == 1) {
            createPlaceholderMatch(event, 1, 1, allMatches, currentRoundMatches);
        } else {
            for (int i = 0; i < numPools; i++) {
                createPlaceholderMatch(event, 1, i + 1, allMatches, currentRoundMatches);
            }
        }

        int matchCount = currentRoundMatches.size();

        while (matchCount > 1) {
            roundNumber++;
            int nextRoundMatchCount = (int) Math.ceil((double) matchCount / 2);

            List<Match> nextRoundMatches = new ArrayList<>();
            for (int i = 0; i < nextRoundMatchCount; i++) {
                createPlaceholderMatch(event, roundNumber, i + 1, allMatches, nextRoundMatches);
            }

            matchCount = nextRoundMatchCount;
            currentRoundMatches = nextRoundMatches;
        }

        // Add 3rd Place Match if there's at least a semifinal round
        if (roundNumber >= 2) {
            Match thirdPlace = new Match();
            thirdPlace.setEvent(event);
            thirdPlace.setBracketRound(roundNumber); // Same round as finals
            thirdPlace.setBracketPosition(2);
            thirdPlace.setStatus(MatchStatus.pending);
            allMatches.add(thirdPlace);
        }
    }

    private void createPlaceholderMatch(Event event, int round, int position, List<Match> allMatches,
            List<Match> currentRoundList) {
        Match match = new Match();
        match.setEvent(event);
        match.setBracketRound(round);
        match.setBracketPosition(position);
        match.setStatus(MatchStatus.pending);

        allMatches.add(match);
        if (currentRoundList != null) {
            currentRoundList.add(match);
        }
    }

    @Override
    public void deleteEvent(UUID id, String username) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        verifyOwnership(event.getTournament(), username);
        eventRepository.deleteById(id);
    }

    private void verifyOwnership(Tournament tournament, String username) {
        if (tournament.getOwner() == null || !tournament.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to modify this tournament's events");
        }
    }

    private EventDTO convertToDTO(Event event) {
        EventDTO dto = modelMapper.map(event, EventDTO.class);

        // Manual mapping for Pool Team IDs and Sort Matches
        if (event.getPools() != null && dto.getPools() != null) {
            // Sort pools by name to ensure stable ordering (Pool A, Pool B, Pool C...)
            dto.getPools().sort(java.util.Comparator.comparing(PoolDTO::getName));

            for (int i = 0; i < event.getPools().size(); i++) {
                Pool pool = event.getPools().get(i);
                // Find matching PoolDTO
                for (PoolDTO poolDTO : dto.getPools()) {
                    if (poolDTO.getId().equals(pool.getId())) {
                        // Sort Teams by CreatedAt to respect input order
                        if (pool.getTeams() != null) {
                            poolDTO.setTeamIds(pool.getTeams().stream()
                                    .sorted(java.util.Comparator.comparing(Team::getCreatedAt,
                                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                                    .map(Team::getId)
                                    .collect(Collectors.toList()));
                        }

                        // Sort Matches: Round (asc), then CreatedAt (asc), then ID (asc) for stability
                        if (poolDTO.getMatches() != null) {
                            poolDTO.getMatches().sort(java.util.Comparator.comparing(MatchDTO::getBracketRound,
                                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                    .thenComparing(MatchDTO::getCreatedAt,
                                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                    .thenComparing(MatchDTO::getId));
                        }

                        // Sort Standings: Wins (desc), PointDiff (desc), PointsFor (desc)
                        if (poolDTO.getStandings() != null) {
                            poolDTO.getStandings().sort((s1, s2) -> {
                                if (s2.getWins() != s1.getWins())
                                    return s2.getWins() - s1.getWins();
                                if (s2.getPointDifferential() != s1.getPointDifferential())
                                    return s2.getPointDifferential() - s1.getPointDifferential();
                                return s2.getPointsFor() - s1.getPointsFor();
                            });
                        }
                        break;
                    }
                }
            }
        }

        // Populate Elimination Bracket DTO
        List<Match> bracketMatches = event.getMatches().stream()
                .filter(m -> m.getPool() == null && m.getBracketRound() != null)
                .collect(Collectors.toList());

        if (!bracketMatches.isEmpty()) {
            EliminationBracketDTO bracketDTO = new EliminationBracketDTO();
            bracketDTO.setEventId(event.getId());

            // Find max round number to identify Finals
            int maxRound = bracketMatches.stream()
                    .mapToInt(Match::getBracketRound)
                    .max().orElse(0);

            // Champion logic (Finals is maxRound, Pos 1)
            Match finalMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == maxRound && m.getBracketPosition() == 1)
                    .findFirst().orElse(null);

            if (finalMatch != null && finalMatch.getWinner() != null) {
                bracketDTO.setChampion(finalMatch.getWinner().getId());
            }

            // Third place match (maxRound, Pos 2)
            Match thirdPlaceMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == maxRound && m.getBracketPosition() == 2)
                    .findFirst().orElse(null);
            if (thirdPlaceMatch != null) {
                bracketDTO.setThirdPlaceMatch(modelMapper.map(thirdPlaceMatch, MatchDTO.class));
                if (thirdPlaceMatch.getWinner() != null) {
                    bracketDTO.setThirdPlaceTeamId(thirdPlaceMatch.getWinner().getId());
                }
            }

            // Group matches into rounds
            List<BracketRoundDTO> roundDTOs = new ArrayList<>();

            for (int r = 1; r <= maxRound; r++) {
                int currentRound = r;
                List<Match> roundMatches = bracketMatches.stream()
                        .filter(m -> m.getBracketRound() == currentRound)
                        .sorted(java.util.Comparator.comparing(Match::getBracketPosition))
                        .collect(Collectors.toList());

                // For the final round, exclude the 3rd place match from the main list
                if (currentRound == maxRound && thirdPlaceMatch != null) {
                    roundMatches.removeIf(m -> m.getBracketPosition() == 2);
                }

                if (!roundMatches.isEmpty()) {
                    BracketRoundDTO roundDTO = new BracketRoundDTO();
                    roundDTO.setRoundNumber(currentRound);
                    roundDTO.setName(getRoundName(currentRound, maxRound));
                    roundDTO.setMatches(roundMatches.stream()
                            .map(m -> modelMapper.map(m, MatchDTO.class))
                            .collect(Collectors.toList()));
                    roundDTOs.add(roundDTO);
                }
            }

            bracketDTO.setRounds(roundDTOs);
            dto.setEliminationBracket(bracketDTO);
        }

        return dto;
    }

    private String getRoundName(int roundNumber, int totalRounds) {
        if (roundNumber == totalRounds)
            return "Finals";
        if (roundNumber == totalRounds - 1)
            return "Semifinals";
        if (roundNumber == totalRounds - 2)
            return "Quarterfinals";
        return "Round " + roundNumber;
    }
}
```

- [ ] **Step 3: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: remaining errors only in `MatchServiceImpl.java`, `EventController.java`/`TournamentController.java`, `MatchController.java`, `BackendIntegrationTest.java` (not yet updated).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/backend/service/EventService.java backend/src/main/java/com/example/backend/service/impl/EventServiceImpl.java
git commit -m "feat: swap EventService/Impl role, carry round-robin and bracket-generation logic, add ownership checks"
```

---

### Task 7: Rewrite `MatchService`/`MatchServiceImpl`

**Files:**
- Modify: `backend/src/main/java/com/example/backend/service/MatchService.java`
- Modify: `backend/src/main/java/com/example/backend/service/impl/MatchServiceImpl.java`

**Interfaces:**
- Consumes: `Event`, `Tournament`, `Pool`, `Team`, `Match`, `PoolStanding` (Task 2); `MatchRepository`, `PoolRepository`, `PoolStandingRepository`, `EventRepository` (Task 3); `MatchDTO`, `ScoreUpdateRequest` (unchanged) (Task 4); `MatchStatus`, `EventStatus` (Task 1).
- Produces: `MatchService.updateScore(UUID matchId, ScoreUpdateRequest request, String username)` — consumed by Task 8 (`MatchController`).

- [ ] **Step 1: Rewrite `MatchService.java`**

```java
package com.example.backend.service;

import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreUpdateRequest;

import java.util.UUID;

public interface MatchService {
    MatchDTO updateScore(UUID matchId, ScoreUpdateRequest request, String username);
}
```

- [ ] **Step 2: Rewrite `MatchServiceImpl.java`**

```java
package com.example.backend.service.impl;

import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import com.example.backend.entity.*;
import com.example.backend.enums.EventStatus;
import com.example.backend.enums.MatchStatus;
import com.example.backend.repository.*;
import com.example.backend.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final PoolStandingRepository poolStandingRepository;
    private final PoolRepository poolRepository;
    private final EventRepository eventRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public MatchDTO updateScore(UUID matchId, ScoreUpdateRequest request, String username) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        verifyOwnership(match.getEvent(), username);

        match.setTeam1Score(request.getTeam1Score());
        match.setTeam2Score(request.getTeam2Score());
        match.setStatus(MatchStatus.completed);

        // Determine winner
        if (request.getTeam1Score() > request.getTeam2Score()) {
            match.setWinner(match.getTeam1());
        } else if (request.getTeam2Score() > request.getTeam1Score()) {
            match.setWinner(match.getTeam2());
        } else {
            // Draw? Spec doesn't clarify. Pickleball usually no draws.
            // For now, leave winner null if draw, but set status completed.
        }

        matchRepository.saveAndFlush(match);

        try {
            if (match.getPool() != null) {
                updatePoolStandings(match);
                checkAndAdvancePoolWinners(match.getPool());
            } else if (match.getBracketRound() != null) {
                advanceInBracket(match);
            }

            // Check and update event lifecycle status
            updateEventStatus(match.getEvent());
            eventRepository.save(match.getEvent());

        } catch (Exception e) {
            // Log error but don't fail the score update
            System.err.println("Error advancing tournament state: " + e.getMessage());
            e.printStackTrace();
        }

        return modelMapper.map(match, MatchDTO.class);
    }

    private void verifyOwnership(Event event, String username) {
        Tournament tournament = event.getTournament();
        if (tournament.getOwner() == null || !tournament.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to update this match");
        }
    }

    private void updatePoolStandings(Match match) {
        Pool pool = match.getPool();
        // Recalculate ALL standings for this pool from scratch to ensure mathematical
        // correctness and avoid incremental drift.

        List<Match> poolMatches = matchRepository.findByPoolId(pool.getId());
        List<PoolStanding> standings = poolStandingRepository.findByPoolId(pool.getId());

        for (PoolStanding standing : standings) {
            standing.setWins(0);
            standing.setLosses(0);
            standing.setPointsFor(0);
            standing.setPointsAgainst(0);
            standing.setPointDifferential(0);
        }

        for (Match m : poolMatches) {
            if (m.getStatus() == MatchStatus.completed && m.getTeam1() != null && m.getTeam2() != null) {
                int s1 = m.getTeam1Score() != null ? m.getTeam1Score() : 0;
                int s2 = m.getTeam2Score() != null ? m.getTeam2Score() : 0;

                updateStandingFromScratch(standings, m.getTeam1().getId(), s1, s2);
                updateStandingFromScratch(standings, m.getTeam2().getId(), s2, s1);
            }
        }

        poolStandingRepository.saveAll(standings);
    }

    private void updateStandingFromScratch(List<PoolStanding> standings, UUID teamId, int scored, int allowed) {
        PoolStanding s = standings.stream()
                .filter(ps -> ps.getTeam().getId().equals(teamId))
                .findFirst().orElse(null);

        if (s != null) {
            s.setPointsFor(s.getPointsFor() + scored);
            s.setPointsAgainst(s.getPointsAgainst() + allowed);
            s.setPointDifferential(s.getPointsFor() - s.getPointsAgainst());

            if (scored > allowed) {
                s.setWins(s.getWins() + 1);
            } else if (scored < allowed) {
                s.setLosses(s.getLosses() + 1);
            }
        }
    }

    private void checkAndAdvancePoolWinners(Pool pool) {
        List<Match> poolMatches = matchRepository.findByPoolId(pool.getId());

        boolean allComplete = poolMatches.stream()
                .allMatch(m -> m.getStatus() == MatchStatus.completed);

        if (!allComplete)
            return;

        pool.setComplete(true);
        poolRepository.save(pool);

        List<PoolStanding> standings = poolStandingRepository.findByPoolId(pool.getId());

        standings.sort((s1, s2) -> {
            if (s2.getWins() != s1.getWins())
                return s2.getWins() - s1.getWins();
            if (s2.getPointDifferential() != s1.getPointDifferential())
                return s2.getPointDifferential() - s1.getPointDifferential();
            return s2.getPointsFor() - s1.getPointsFor();
        });

        if (standings.size() < 1)
            return;

        Team seed1 = standings.get(0).getTeam();
        Team seed2 = standings.size() > 1 ? standings.get(1).getTeam() : null;

        Event event = pool.getEvent();

        List<Pool> allPools = poolRepository.findByEventId(event.getId());
        allPools.sort(java.util.Comparator.comparing(Pool::getName));

        int poolIndex = -1;
        for (int i = 0; i < allPools.size(); i++) {
            if (allPools.get(i).getId().equals(pool.getId())) {
                poolIndex = i;
                break;
            }
        }

        if (poolIndex == -1) {
            System.err.println("Could not find pool index for pool: " + pool.getName());
            return;
        }

        int totalPools = allPools.size();

        List<Match> bracketMatches = matchRepository.findByEventId(event.getId()).stream()
                .filter(m -> m.getPool() == null)
                .toList();

        if (totalPools == 1) {
            Match finalMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 1 && m.getBracketPosition() == 1)
                    .findFirst().orElse(null);

            if (finalMatch != null) {
                finalMatch.setTeam1(seed1);
                if (seed2 != null)
                    finalMatch.setTeam2(seed2);

                finalMatch.setStatus(MatchStatus.pending);
                matchRepository.save(finalMatch);
            }
        } else {
            int matchPosForSeed1 = poolIndex + 1;
            Match match1 = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 1 && m.getBracketPosition() == matchPosForSeed1)
                    .findFirst().orElse(null);

            if (match1 != null) {
                match1.setTeam1(seed1);
                matchRepository.save(match1);
            }

            int matchIndexForSeed2 = (poolIndex - 1 + totalPools) % totalPools;
            int matchPosForSeed2 = matchIndexForSeed2 + 1;

            Match match2 = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 1 && m.getBracketPosition() == matchPosForSeed2)
                    .findFirst().orElse(null);

            if (match2 != null && seed2 != null) {
                match2.setTeam2(seed2);
                matchRepository.save(match2);
            }
        }
    }

    private void advanceInBracket(Match match) {
        if (match.getWinner() == null || match.getBracketRound() == null || match.getBracketPosition() == null)
            return;

        int currentRound = match.getBracketRound();
        int currentPos = match.getBracketPosition();

        int nextRound = currentRound + 1;
        int nextPos = (currentPos + 1) / 2;

        List<Match> allEliminationMatches = matchRepository.findByEventId(match.getEvent().getId())
                .stream()
                .filter(m -> m.getPool() == null)
                .toList();

        Match nextMatch = allEliminationMatches.stream()
                .filter(m -> m.getBracketRound() != null &&
                        m.getBracketRound() == nextRound &&
                        m.getBracketPosition() != null &&
                        m.getBracketPosition() == nextPos)
                .findFirst()
                .orElse(null);

        if (nextMatch != null) {
            if (currentPos % 2 != 0) { // Odd position -> Team 1
                nextMatch.setTeam1(match.getWinner());

                long currentRoundMatchCount = allEliminationMatches.stream()
                        .filter(m -> m.getBracketRound() != null && m.getBracketRound() == currentRound)
                        .count();

                // If there is no opponent match (currentPos == count), it's a bye
                if (currentPos == currentRoundMatchCount) {
                    nextMatch.setTeam2Score(0);
                    nextMatch.setTeam1Score(0);
                    nextMatch.setWinner(match.getWinner()); // Auto-win
                    nextMatch.setStatus(MatchStatus.completed);

                    matchRepository.save(nextMatch);

                    advanceInBracket(nextMatch);
                    return;
                }

            } else { // Even position -> Team 2
                nextMatch.setTeam2(match.getWinner());
            }
            matchRepository.save(nextMatch);
        }

        populateThirdPlaceMatch(match, allEliminationMatches);
    }

    private void populateThirdPlaceMatch(Match match, List<Match> allEliminationMatches) {
        if (match.getWinner() == null)
            return;

        int currentRound = match.getBracketRound();

        int maxRound = allEliminationMatches.stream()
                .filter(m -> m.getBracketRound() != null)
                .mapToInt(Match::getBracketRound)
                .max().orElse(0);

        // This match must be in the round just before the Finals (Semifinals)
        if (currentRound != maxRound - 1)
            return;

        Match thirdPlaceMatch = allEliminationMatches.stream()
                .filter(m -> m.getBracketRound() != null &&
                        m.getBracketRound() == maxRound &&
                        m.getBracketPosition() != null &&
                        m.getBracketPosition() == 2)
                .findFirst().orElse(null);

        if (thirdPlaceMatch == null)
            return;

        Team loser = match.getTeam1().getId().equals(match.getWinner().getId())
                ? match.getTeam2()
                : match.getTeam1();

        if (loser == null)
            return;

        if (thirdPlaceMatch.getTeam1() == null) {
            thirdPlaceMatch.setTeam1(loser);
        } else if (thirdPlaceMatch.getTeam2() == null) {
            thirdPlaceMatch.setTeam2(loser);
        }
        matchRepository.save(thirdPlaceMatch);
    }

    private void updateEventStatus(Event event) {
        // 1. Check for transition from POOL_PLAY to ELIMINATION
        if (event.getStatus() == EventStatus.pool_play) {
            boolean allPoolsComplete = poolRepository.findByEventId(event.getId()).stream()
                    .allMatch(Pool::isComplete);

            if (allPoolsComplete) {
                event.setStatus(EventStatus.elimination);
            }
        }

        // 2. Check for transition to COMPLETED
        if (event.getStatus() == EventStatus.elimination) {
            List<Match> eliminationMatches = matchRepository.findByEventId(event.getId()).stream()
                    .filter(m -> m.getPool() == null)
                    .toList();

            int maxRound = eliminationMatches.stream()
                    .filter(m -> m.getBracketRound() != null)
                    .mapToInt(Match::getBracketRound)
                    .max().orElse(0);

            // Exclude the 3rd place match (optional) from the completion check
            boolean allComplete = eliminationMatches.stream()
                    .filter(m -> !(m.getBracketRound() != null && m.getBracketRound() == maxRound
                            && m.getBracketPosition() != null && m.getBracketPosition() == 2))
                    .allMatch(m -> m.getStatus() == MatchStatus.completed);

            if (allComplete && !eliminationMatches.isEmpty()) {
                event.setStatus(EventStatus.completed);
            }
        }
    }
}
```

- [ ] **Step 3: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: remaining errors only in `EventController.java`/`TournamentController.java`, `MatchController.java`, `BackendIntegrationTest.java`.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/backend/service/MatchService.java backend/src/main/java/com/example/backend/service/impl/MatchServiceImpl.java
git commit -m "feat: add ownership check to MatchService.updateScore, rename Tournament refs to Event"
```

---

### Task 8: Rewrite controllers

**Files:**
- Modify: `backend/src/main/java/com/example/backend/controller/TournamentController.java` (full rewrite — was `EventController.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/controller/EventController.java` (full rewrite — was `TournamentController.java`'s content)
- Modify: `backend/src/main/java/com/example/backend/controller/MatchController.java`
- Not modified: `backend/src/main/java/com/example/backend/controller/SpaForwardingController.java`, `AuthController.java`

**Interfaces:**
- Consumes: `TournamentService` (Task 5), `EventService` (Task 6), `MatchService` (Task 7), `ApiResponse` (unchanged).
- Produces: `POST/GET/PUT/DELETE /api/v1/tournaments`, `GET /api/v1/tournaments/{id}/events`, `POST/GET/DELETE /api/v1/events`, `PUT /api/v1/events/{eventId}/matches/{matchId}/score` — consumed by Task 9 (`SecurityConfig`).

- [ ] **Step 1: Rewrite `TournamentController.java`**

```java
package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TournamentDTO>>> getAllTournaments(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getAllTournaments(authentication.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TournamentDTO>> createTournament(@RequestBody CreateTournamentRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tournamentService.createTournament(request, authentication.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TournamentDTO>> getTournamentById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getTournamentById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TournamentDTO>> updateTournament(@PathVariable UUID id,
            @RequestBody UpdateTournamentRequest request, Authentication authentication) {
        return ResponseEntity
                .ok(ApiResponse.success(tournamentService.updateTournament(id, request, authentication.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTournament(@PathVariable UUID id, Authentication authentication) {
        tournamentService.deleteTournament(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<ApiResponse<List<EventDTO>>> getEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getEvents(id)));
    }
}
```

Note: today's `EventController` also has `addTournamentToEvent`/`removeTournamentFromEvent` — deleted per spec (re-parenting no longer applies once `Event.tournament` is required).

- [ ] **Step 2: Rewrite `EventController.java`**

```java
package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventDTO>>> getAllEvents(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getAllEvents(authentication.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventDTO>> createEvent(@RequestBody CreateEventRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(eventService.createEvent(request, authentication.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDTO>> getEventById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getEventById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable UUID id, Authentication authentication) {
        eventService.deleteEvent(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

- [ ] **Step 3: Rewrite `MatchController.java`**

```java
package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import com.example.backend.service.EventService;
import com.example.backend.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final EventService eventService; // Needed to fetch full state
    private final SimpMessagingTemplate messagingTemplate;

    @PutMapping("/{matchId}/score")
    public ResponseEntity<ApiResponse<MatchDTO>> updateScore(
            @PathVariable UUID eventId,
            @PathVariable UUID matchId,
            @RequestBody ScoreUpdateRequest request,
            Authentication authentication) {

        // 1. Update the score (ownership verified inside the service)
        MatchDTO updatedMatch = matchService.updateScore(matchId, request, authentication.getName());

        // 2. Fetch the full updated event state
        var fullEvent = eventService.getEventById(eventId);

        // 3. Broadcast to subscribers
        messagingTemplate.convertAndSend("/topic/event/" + eventId, fullEvent);

        return ResponseEntity.ok(ApiResponse.success(updatedMatch));
    }
}
```

Note: WebSocket topic renamed `/topic/tournament/{id}` → `/topic/event/{id}` to match the new resource name — the frontend follow-up spec must pick up this new topic name.

- [ ] **Step 4: Compile check**

Run: `cd backend && ./mvnw compile -q`
Expected: only `BackendIntegrationTest.java` (test source) still fails to compile.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/backend/controller/
git commit -m "feat: swap Tournament/EventController roles, add auth to write ops, rename match score path and WS topic"
```

---

### Task 9: Update `SecurityConfig.java` permit-list

**Files:**
- Modify: `backend/src/main/java/com/example/backend/config/SecurityConfig.java:73-75`

**Interfaces:**
- Consumes: nothing new (same `HttpSecurity`/`HttpMethod` APIs already imported).
- Produces: the corrected public-read matcher list, required for Task 10's tests and the (deferred) public viewer to work.

- [ ] **Step 1: Replace the three GET matchers**

In `filterChain(HttpSecurity http)`, replace:

```java
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/{id}/tournaments").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tournaments/{id}").permitAll()
```

with:

```java
                        .requestMatchers(HttpMethod.GET, "/api/v1/tournaments/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tournaments/{id}/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/{id}").permitAll()
```

Everything else in `SecurityConfig.java` (CORS config, JWT filter registration, SPA static-route permits) is unchanged.

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/example/backend/config/SecurityConfig.java
git commit -m "fix: update SecurityConfig permit-list to the renamed Tournament/Event GET paths"
```

---

### Task 10: Update `BackendIntegrationTest.java` to the new create-tournament-then-create-event flow

**Files:**
- Modify: `backend/src/test/java/com/example/backend/BackendIntegrationTest.java` (full rewrite)

**Interfaces:**
- Consumes: `CreateTournamentRequest`, `CreateEventRequest`, `PoolConfigDTO` (Task 4), `/api/v1/tournaments`, `/api/v1/events` (Task 8).

Note: today's file imports `AutoConfigureMockMvc` from the Spring Boot 3-era package `org.springframework.boot.test.autoconfigure.web.servlet`, which doesn't exist in this project's Spring Boot 4.0.2 — a pre-existing baseline compile break, confirmed by inspecting `spring-boot-webmvc-test-4.0.2.jar`, unrelated to the Tournament/Event swap. The rewrite below uses the correct Boot 4 package, `org.springframework.boot.webmvc.test.autoconfigure`.

- [ ] **Step 1: Rewrite the test file**

```java
package com.example.backend;

import com.example.backend.dto.AuthDtos.LoginRequest;
import com.example.backend.dto.AuthDtos.SignupRequest;
import com.example.backend.dto.CreateEventRequest;
import com.example.backend.dto.CreateTournamentRequest;
import com.example.backend.dto.PoolConfigDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BackendIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String token;
    private static UUID tournamentId;
    private static UUID eventId;

    @Test
    @Order(1)
    void testSignupAndLogin() throws Exception {
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setUsername("testuser");
        signupRequest.setPassword("password123");
        signupRequest.setPhoneNumber("+1234567890");

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").exists());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("+1234567890");
        loginRequest.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        token = objectMapper.readTree(response).path("data").path("token").asText();
    }

    @Test
    @Order(2)
    void testCreateTournament() throws Exception {
        CreateTournamentRequest tournamentRequest = new CreateTournamentRequest();
        tournamentRequest.setName("Test Tournament");
        tournamentRequest.setStartDate(LocalDate.now());
        tournamentRequest.setEndDate(LocalDate.now().plusDays(2));

        MvcResult result = mockMvc.perform(post("/api/v1/tournaments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(tournamentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        tournamentId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    @Test
    @Order(3)
    void testCreateEvent() throws Exception {
        PoolConfigDTO poolA = new PoolConfigDTO();
        poolA.setName("Pool A");
        poolA.setTeamNames(List.of("Team 1", "Team 2", "Team 3"));

        CreateEventRequest request = new CreateEventRequest();
        request.setName("Test Event");
        request.setTournamentId(tournamentId);
        request.setPools(Collections.singletonList(poolA));

        MvcResult result = mockMvc.perform(post("/api/v1/events")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.pools").isArray())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        eventId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
    }

    @Test
    @Order(4)
    void testGetTournaments() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/test/java/com/example/backend/BackendIntegrationTest.java
git commit -m "test: update integration test to create-tournament-then-create-event flow"
```

---

### Task 11: Full compile and test verification

**Files:** none (verification only).

- [ ] **Step 1: Clean compile**

Run: `cd backend && ./mvnw clean compile -q`
Expected: exits 0, no errors.

- [ ] **Step 2: Run the full test suite**

Run: `cd backend && ./mvnw test`
Expected: `BackendApplicationTests#contextLoads` and all 4 `BackendIntegrationTest` methods (`testSignupAndLogin`, `testCreateTournament`, `testCreateEvent`, `testGetTournaments`) pass. `BUILD SUCCESS`.

- [ ] **Step 3: If anything fails**

Re-check the failing class against its corresponding task above — every file's exact new content is specified in Tasks 1–10, so a failure here means a step was skipped or mistyped, not a design gap. Fix and re-run Step 2.

- [ ] **Step 4: Final commit (only if Step 3 required fixes)**

```bash
git add backend/
git commit -m "fix: resolve remaining compile/test issues from Tournament/Event swap"
```

---

## Self-Review

**Spec coverage:** Entity model → Task 2. Enum extraction → Task 1. File-level plan (entities/repos/services/controllers/DTOs) → Tasks 2–8. Repository query renames → Task 3. API surface / URL paths → Task 8. Re-parenting endpoints deleted → Task 8 Step 1 note. `SecurityConfig` permit-list (caught during plan-writing, not originally in the spec's file table) → Task 9. Ownership-check split (public reads vs. owner-only writes, resolved during plan-writing) → Tasks 5–8. Tests → Task 10. Migration (schema reset, no script) → no task needed, confirmed as a Global Constraint. Every spec section has a task; no gaps found.

**Placeholder scan:** No "TBD"/"similar to Task N" — every file has full content. Clean.

**Type consistency:** `TournamentService`/`EventService`/`MatchService` interface signatures in Tasks 5–7 match exactly what Task 8's controllers call. `EventDTO.status`/`MatchDTO.status` stay `String` (ModelMapper auto-converts the enum, unchanged from today's pattern — not touched by the enum extraction, which only affects the entity-side type). `PoolConfigDTO`, `PoolStandingDTO`, `BracketRoundDTO`, `ApiResponse`, `ScoreUpdateRequest` confirmed unchanged and correctly referenced as-is throughout.
