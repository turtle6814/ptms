# Pickleball Tournament Management System (PTMS) — Product Requirements Document

## 1. Product Overview

**PTMS** is a full-stack web application for organizing, managing, and sharing pickleball (or general sports) tournaments. Organizers create **Events** (which group multiple tournaments), set up **Tournaments** with **Pools** and **Teams**, manage live match scoring, and share a public real-time viewer link with spectators via QR code.

| Layer     | Stack                                       |
|-----------|---------------------------------------------|
| Frontend  | React 18 + TypeScript, Vite, React Router   |
| Backend   | Spring Boot 3, Spring Security (JWT), JPA   |
| Real-time | WebSockets (STOMP over SockJS)               |
| Styling   | Vanilla CSS (dark theme, glassmorphism)      |

---

## 2. User Roles

| Role       | Description                                                    |
|------------|----------------------------------------------------------------|
| **Admin**  | Authenticated user who creates events/tournaments and manages scores. |
| **Viewer** | Unauthenticated visitor who views live tournament data via a shared link. |

---

## 3. Functional Requirements

### 3.1 Authentication

| ID    | Requirement                                                                                  |
|-------|----------------------------------------------------------------------------------------------|
| AU-01 | Users can **sign up** with username, phone number, and password (`/signup`).                  |
| AU-02 | Users can **log in** with phone number and password (`/login`).                               |
| AU-03 | On successful auth, a JWT token is stored in `localStorage` and sent as a Bearer header.      |
| AU-04 | Protected routes (`/admin`, `/setup`, `/events`, `/events/:id`) redirect to `/login` if unauthenticated. |
| AU-05 | Users can **log out**, which clears the stored token.                                         |
| AU-06 | Landing page (`/`) redirects authenticated users to `/admin`.                                 |

### 3.2 Events Management

| ID    | Requirement                                                                 |
|-------|-----------------------------------------------------------------------------|
| EV-01 | Admin can **view a list of all events** (`/events`).                        |
| EV-02 | Admin can **create a new event** with a name and optional description via a modal. |
| EV-03 | After creating an event, the user is navigated to the event detail page.    |
| EV-04 | Admin can **view event details** (`/events/:eventId`) showing event header, description, and list of tournaments. |
| EV-05 | Admin can **edit event name and description** inline on the detail page.    |
| EV-06 | Admin can **delete an event**, which also cascades deletion of all associated tournaments. |
| EV-07 | Admin can **add a new tournament** to an event (navigates to `/setup?eventId=...`). |
| EV-08 | Admin can **remove a tournament** from an event (always-visible remove button). |

### 3.3 Tournament Setup

| ID    | Requirement                                                                             |
|-------|-----------------------------------------------------------------------------------------|
| TS-01 | Admin can **create a tournament** with a name and one or more pools (`/setup`).          |
| TS-02 | Each pool is given an auto-incrementing letter name (Pool A, Pool B, Pool C…).           |
| TS-03 | Each pool requires **at least 2 teams** with unique names.                               |
| TS-04 | Admin can **add/remove team slots** within a pool (minimum 2).                           |
| TS-05 | Admin can **add/remove pools** (minimum 1 pool).                                         |
| TS-06 | If created from an event context (`?eventId=...`), the tournament is linked to that event.|
| TS-07 | On successful creation, navigate back to the event detail page or admin dashboard.       |

### 3.4 Admin Dashboard

| ID    | Requirement                                                                           |
|-------|---------------------------------------------------------------------------------------|
| AD-01 | Admin can see a **sidebar listing events** with expandable tournament dropdowns.       |
| AD-02 | Selecting a tournament loads its details in the main content area.                     |
| AD-03 | Tournament detail shows a **tabbed view**: Pool Play and Playoffs.                    |
| AD-04 | **Pool Play tab** displays pool standings (wins, losses, PF, PA, PD) and match cards in a side-by-side grid. |
| AD-05 | Admin can **update match scores** by entering scores for each team on a match card.    |
| AD-06 | After a score update, pool standings recalculate automatically.                        |
| AD-07 | When all pool matches are complete, the **elimination bracket is generated** with top 2 from each pool. |
| AD-08 | **Playoffs tab** displays an elimination bracket with round connectors.                |
| AD-09 | Admin can update bracket match scores; winners automatically advance to the next round.|
| AD-10 | Admin can **share an event** via a QR code modal (generates `/view/event/:eventId` link). |
| AD-11 | Admin can **delete a tournament** from the sidebar.                                    |
| AD-12 | Admin can **refresh** tournament data manually.                                        |
| AD-13 | Real-time updates via **WebSocket** subscription per tournament.                       |

### 3.5 Public Event Viewer

| ID    | Requirement                                                                        |
|-------|------------------------------------------------------------------------------------|
| VW-01 | Viewers can access `/view/event/:eventId` **without authentication**.              |
| VW-02 | Page displays the **event name** and a **tournament selector dropdown**.            |
| VW-03 | Selecting a tournament shows its pool standings, match results, and elimination bracket (read-only). |
| VW-04 | A **green "Live" badge** is shown when the tournament is not yet completed.         |
| VW-05 | A "Last updated" timestamp is displayed.                                           |
| VW-06 | Data refreshes via WebSocket subscription and polling fallback.                    |
| VW-07 | If the event has no tournaments, a "No Tournaments" empty state is shown.          |

### 3.6 QR Code Sharing

| ID    | Requirement                                                               |
|-------|---------------------------------------------------------------------------|
| QR-01 | Share modal generates a **QR code** for the event viewer URL.             |
| QR-02 | The shareable URL is displayed and can be **copied to clipboard**.        |
| QR-03 | Share button is accessible from the event header in the admin sidebar and the tournament detail header. |

---

## 4. Data Models

### Event
| Field          | Type       | Notes                     |
|----------------|------------|---------------------------|
| id             | UUID       | Primary key               |
| name           | String     | Required                  |
| description    | String?    | Optional                  |
| startDate      | Date?      | Optional                  |
| endDate        | Date?      | Optional                  |
| tournamentIds  | UUID[]     | Linked tournaments        |
| createdAt      | DateTime   |                           |
| updatedAt      | DateTime   |                           |

### Tournament
| Field              | Type       | Notes                                       |
|--------------------|------------|---------------------------------------------|
| id                 | UUID       | Primary key                                 |
| eventId            | UUID?      | Nullable, links to parent Event             |
| name               | String     | Required                                    |
| status             | Enum       | `setup`, `pool_play`, `elimination`, `completed` |
| teams              | Team[]     |                                             |
| pools              | Pool[]     |                                             |
| eliminationBracket | Bracket?   | Generated after pool play completes         |

### Pool
| Field      | Type            | Notes                       |
|------------|-----------------|------------------------------|
| id         | UUID            |                              |
| name       | String          | e.g. "Pool A"               |
| teamIds    | UUID[]          |                              |
| matches    | Match[]         | Round-robin generated        |
| standings  | PoolStanding[]  | Ordered by wins, then PD     |
| isComplete | Boolean         |                              |

### Match
| Field          | Type    | Notes                                       |
|----------------|---------|---------------------------------------------|
| id             | UUID    |                                             |
| team1Id        | UUID    |                                             |
| team2Id        | UUID    |                                             |
| team1Score     | Int?    |                                             |
| team2Score     | Int?    |                                             |
| winnerId       | UUID?   | Set after score submission                  |
| status         | Enum    | `pending`, `in_progress`, `completed`       |
| bracketRound   | Int?    | For elimination matches                     |
| bracketPosition| Int?    | For elimination matches                     |

---

## 5. Page Routes

| Route                    | Page              | Auth Required | Description                      |
|--------------------------|-------------------|---------------|----------------------------------|
| `/`                      | LandingPage       | No            | Marketing page, redirects if logged in |
| `/login`                 | LoginPage         | No            | Phone + password login           |
| `/signup`                | SignupPage        | No            | Username + phone + password      |
| `/admin`                 | AdminDashboard    | Yes           | Main management dashboard        |
| `/setup`                 | TournamentSetup   | Yes           | Create new tournament            |
| `/events`                | EventsPage        | Yes           | List all events                  |
| `/events/:eventId`       | EventDetailPage   | Yes           | Event detail + tournaments       |
| `/view/event/:eventId`   | EventViewerPage   | No            | Public live viewer               |
| `*`                      | LandingPage       | No            | Fallback                        |

---

## 6. API Endpoints

### Auth
| Method | Endpoint          | Description           |
|--------|-------------------|-----------------------|
| POST   | `/auth/login`     | Login with credentials|
| POST   | `/auth/signup`    | Register new user     |
| GET    | `/auth/me`        | Get current user info |

### Events
| Method | Endpoint                                  | Description                    |
|--------|-------------------------------------------|--------------------------------|
| GET    | `/events`                                 | List all events                |
| GET    | `/events/:id`                             | Get event by ID                |
| POST   | `/events`                                 | Create event                   |
| PUT    | `/events/:id`                             | Update event                   |
| DELETE | `/events/:id`                             | Delete event (cascades)        |
| POST   | `/events/:eventId/tournaments/:tournId`   | Add tournament to event        |
| DELETE | `/events/:eventId/tournaments/:tournId`   | Remove tournament from event   |
| GET    | `/events/:eventId/tournaments`            | Get event's tournaments        |

### Tournaments
| Method | Endpoint                                              | Description          |
|--------|-------------------------------------------------------|----------------------|
| GET    | `/tournaments`                                        | List all tournaments |
| GET    | `/tournaments/:id`                                    | Get tournament by ID |
| POST   | `/tournaments`                                        | Create tournament    |
| DELETE | `/tournaments/:id`                                    | Delete tournament    |
| PUT    | `/tournaments/:tournId/matches/:matchId/score`        | Update match score   |

### WebSocket
| Endpoint                        | Description                     |
|---------------------------------|---------------------------------|
| `ws://localhost:8080/ws`        | STOMP WebSocket connection      |
| `/topic/tournament/:id`         | Subscribe to tournament updates |

---

## 7. Non-Functional Requirements

| ID    | Requirement                                                          |
|-------|----------------------------------------------------------------------|
| NF-01 | Frontend must be **fully responsive** (desktop, tablet, mobile).     |
| NF-02 | Dark theme with premium glassmorphism styling across all pages.      |
| NF-03 | Real-time updates must arrive within **2 seconds** of a score change.|
| NF-04 | JWT tokens must be validated on every protected API call.            |
| NF-05 | Deleting an event must cascade-delete all linked tournaments.        |
| NF-06 | Public viewer page must work without any authentication.             |

---

## 8. Test Scenarios

### 8.1 Authentication Tests
- Sign up with valid credentials → success, redirected to admin
- Sign up with duplicate phone number → error message displayed
- Log in with valid credentials → success, token stored, redirected to admin
- Log in with wrong password → error message displayed
- Access `/admin` without auth → redirected to `/login`
- Log out → token cleared, redirected to landing page

### 8.2 Events Tests
- Create event with name only → event appears in list
- Create event with name + description → both shown on detail page
- Edit event name inline → name updates on save
- Delete event → event and its tournaments removed
- View event detail → shows linked tournaments

### 8.3 Tournament Setup Tests
- Create tournament with 1 pool, 2 teams → success
- Create tournament with 3 pools, 4 teams each → success
- Try to create with empty tournament name → validation error
- Try to create with less than 2 teams in a pool → validation error
- Try to create with duplicate team names in pool → validation error
- Add/remove team slots → UI updates correctly
- Add/remove pools → auto-renaming (Pool A, B, C…)

### 8.4 Admin Dashboard Tests
- Select tournament from sidebar → detail loads
- Update pool match score → standings recalculate
- Complete all pool matches → bracket auto-generates
- Update bracket match score → winner advances
- Complete all bracket matches → tournament status becomes "completed"
- Share event → QR code modal shows correct URL
- Delete tournament → removed from sidebar and event

### 8.5 Public Viewer Tests
- Access `/view/event/:eventId` without login → page loads
- Select tournament from dropdown → shows pool/bracket data
- Live badge shows green when tournament is active
- Score updates reflect in real-time via WebSocket
- Invalid event ID → "Event Not Found" error state
- Event with no tournaments → "No Tournaments" empty state

### 8.6 Responsive Design Tests
- All pages render correctly at mobile (375px), tablet (768px), and desktop (1280px+)
- Tournament selector dropdown extends full-width on mobile
- Pool grid collapses to single column on mobile
- Navigation header is usable on all screen sizes
