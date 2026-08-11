# Frontend Tournament/Event Swap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename and reshape the frontend (`frontend/src/`) to match the already-merged backend Tournament/Event swap — `Tournament` is now the top-level owned container, `Event` is the mandatory child holding pools/teams/matches/bracket. Frontend naming (pages, routes, components) mirrors the backend rename fully, confirmed with the user.

**Architecture:** Mechanical rename+retype mirroring the backend plan's pattern: page pairs trade names (`EventsPage.tsx`→`TournamentsPage.tsx` etc., full-file rewrites with content captured directly, no `git mv` needed), the API layer (`types.ts`/`index.ts`) retypes field-for-field against the new backend DTOs, and every consumer of the renamed types gets its variable names/API calls updated to match. Because `api/types.ts` and `api/index.ts` are consumed by nearly every other file, **intermediate tasks will not type-check cleanly in isolation** — same as the backend plan, this is inherent to a mutual-reference rename, not a task-sizing failure. The first full, clean `tsc`/build checkpoint is Task 11.

**Tech Stack:** React 19 + TypeScript, Vite, React Router 7, axios, `@stomp/stompjs`.

## Global Constraints

- No test runner is configured for the frontend (confirmed in `CLAUDE.md`) — verification is `npm run build` (`tsc -b && vite build`) plus a manual click-through, not automated tests.
- **CSS class name strings are never renamed** — only files that are renamed get their co-located `.css` file renamed alongside them (content copied verbatim, zero changes); files that keep their name keep their CSS untouched. No visual/CSS redesign — explicit non-goal in the spec.
- Full rename scope: pages, routes, component names where they describe the renamed entities (not `MatchCard`/`PoolStandings`/`TournamentTabs`/`QRCodeShare`/`Header`/`ProtectedRoute`, which describe UI concepts and keep their names).
- Backend addendum is in scope: `SpaForwardingController.java` and `SecurityConfig.java` need their SPA route permit lists updated from `/events`,`/events/**` to `/tournaments`,`/tournaments/**` — this is a **replacement**, not an addition (the old `/events` frontend route no longer exists after this plan; there is nothing left for it to serve).
- `Event.tournamentId` is **required**, never optional, everywhere in the new frontend types — reflect this at every call site (no more `eventId || undefined` fallback patterns).

Full source-of-truth for every decision below: `docs/superpowers/specs/2026-08-11-frontend-tournament-event-swap-design.md`.

---

### Task 1: Rewrite `api/types.ts` and `api/index.ts`

**Files:**
- Modify: `frontend/src/api/types.ts` (full rewrite)
- Modify: `frontend/src/api/index.ts` (full rewrite)
- Not modified: `frontend/src/api/client.ts` (generic axios setup, untouched)

**Interfaces:**
- Produces: `Tournament{id,name,description,startDate,endDate,eventIds,createdAt,updatedAt}`, `Event{id,tournamentId,name,status,teams,pools,eliminationBracket,createdAt,updatedAt}`, `CreateTournamentRequest`, `UpdateTournamentRequest`, `CreateEventRequest{name,tournamentId,pools}`, `Match{eventId,...}`, `Pool{eventId,...}`, `EliminationBracket{eventId,...}` — consumed by every later task.
- Produces functions: `getAllTournaments`, `getTournamentById`, `createTournament`, `updateTournament`, `deleteTournament`, `getTournamentEvents`, `getAllEvents`, `getEventById`, `createEvent`, `deleteEvent`, `updateMatchScore(eventId, update)`, `subscribeEvent(eventId, cb)`, `pollEvent`, `generateShareableLink(tournamentId)` — consumed by every page task.

- [ ] **Step 1: Rewrite `types.ts`**

```ts
// ================================
// Tournament Manager - Type Definitions
// Strictly matching OpenAPI Config
// ================================

// ----------------------------------------------------------
// User & Auth Schemas
// ----------------------------------------------------------
export interface User {
  id: string; // uuid
  username: string;
  phoneNumber: string;
  createdAt: string; // date-time
}

export interface LoginRequest {
  phoneNumber: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  phoneNumber: string;
  password: string;
}

export interface AuthResponse {
  user: User;
  token: string;
}

// ----------------------------------------------------------
// Tournament Schemas (top-level owned container)
// ----------------------------------------------------------
export interface Tournament {
  id: string; // uuid
  name: string;
  description?: string | null;
  startDate?: string | null; // date
  endDate?: string | null; // date
  eventIds: string[]; // array of uuid
  createdAt: string; // date-time
  updatedAt: string; // date-time
}

export interface CreateTournamentRequest {
  name: string;
  description?: string | null;
  startDate?: string | null; // date
  endDate?: string | null; // date
}

export interface UpdateTournamentRequest {
  name?: string;
  description?: string | null;
  startDate?: string | null; // date
  endDate?: string | null; // date
}

// ----------------------------------------------------------
// Event Schemas (competition unit)
// ----------------------------------------------------------
export interface Team {
  id: string; // uuid
  name: string;
  createdAt: string; // date-time
}

export interface Match {
  id: string; // uuid
  eventId: string; // uuid
  poolId?: string | null; // uuid
  bracketRound?: number | null;
  bracketPosition?: number | null;
  team1Id: string; // uuid
  team2Id: string; // uuid
  team1Score?: number | null;
  team2Score?: number | null;
  winnerId?: string | null; // uuid
  status: 'pending' | 'in_progress' | 'completed';
  createdAt: string; // date-time
  updatedAt: string; // date-time
}

export interface PoolStanding {
  teamId: string; // uuid
  teamName: string;
  wins: number;
  losses: number;
  pointsFor: number;
  pointsAgainst: number;
  pointDifferential: number;
}

export interface Pool {
  id: string; // uuid
  eventId: string; // uuid
  name: string;
  teamIds: string[]; // uuid[]
  matches: Match[];
  standings: PoolStanding[];
  isComplete: boolean;
}

export interface BracketRound {
  roundNumber: number;
  name: string;
  matches: Match[];
}

export interface EliminationBracket {
  eventId: string; // uuid
  rounds: BracketRound[];
  champion?: string | null; // uuid
  thirdPlaceMatch?: Match | null;
  thirdPlaceTeamId?: string | null; // uuid
}

export interface Event {
  id: string; // uuid
  tournamentId: string; // uuid — required
  name: string;
  status: 'setup' | 'pool_play' | 'elimination' | 'completed';
  teams: Team[];
  pools: Pool[];
  eliminationBracket?: EliminationBracket | null;
  createdAt: string; // date-time
  updatedAt: string; // date-time
}

export interface PoolConfig {
  name: string;
  teamNames: string[];
}

export interface CreateEventRequest {
  name: string;
  tournamentId: string; // required
  pools: PoolConfig[];
}

export interface ScoreUpdateRequest {
  matchId: string; // uuid
  team1Score: number;
  team2Score: number;
}

// ----------------------------------------------------------
// Response Wrappers
// ----------------------------------------------------------
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

export interface ErrorResponse {
  success: boolean;
  error: string;
}
```

- [ ] **Step 2: Rewrite `index.ts`**

```ts
import client from './client';
import {
    ApiResponse,
    LoginRequest,
    SignupRequest,
    AuthResponse,
    User,
    Tournament,
    CreateTournamentRequest,
    UpdateTournamentRequest,
    Event,
    CreateEventRequest,
    ScoreUpdateRequest,
} from './types';

// ================================
// Auth API
// ================================

export async function login(payload: LoginRequest): Promise<ApiResponse<AuthResponse>> {
    try {
        const response = await client.post<ApiResponse<AuthResponse>>('/auth/login', payload);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Login failed',
        };
    }
}

export async function signup(payload: SignupRequest): Promise<ApiResponse<AuthResponse>> {
    try {
        const response = await client.post<ApiResponse<AuthResponse>>('/auth/signup', payload);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Signup failed',
        };
    }
}

export async function getCurrentUser(): Promise<ApiResponse<User>> {
    try {
        const response = await client.get<ApiResponse<User>>('/auth/me');
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to get current user',
        };
    }
}

export async function logout(): Promise<ApiResponse<void>> {
    // Client-side logout only since JWT is stateless (unless we had a blacklist)
    return { success: true };
}

// ================================
// Tournament API (top-level container)
// ================================

export async function getAllTournaments(): Promise<ApiResponse<Tournament[]>> {
    try {
        const response = await client.get<ApiResponse<Tournament[]>>('/tournaments');
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to fetch tournaments',
        };
    }
}

export async function getTournamentById(id: string): Promise<ApiResponse<Tournament>> {
    try {
        const response = await client.get<ApiResponse<Tournament>>(`/tournaments/${id}`);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Tournament not found',
        };
    }
}

export async function createTournament(payload: CreateTournamentRequest): Promise<ApiResponse<Tournament>> {
    try {
        const response = await client.post<ApiResponse<Tournament>>('/tournaments', payload);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to create tournament',
        };
    }
}

export async function updateTournament(id: string, payload: UpdateTournamentRequest): Promise<ApiResponse<Tournament>> {
    try {
        const response = await client.put<ApiResponse<Tournament>>(`/tournaments/${id}`, payload);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to update tournament',
        };
    }
}

export async function deleteTournament(id: string): Promise<ApiResponse<void>> {
    try {
        const response = await client.delete<ApiResponse<void>>(`/tournaments/${id}`);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to delete tournament',
        };
    }
}

export async function getTournamentEvents(tournamentId: string): Promise<ApiResponse<Event[]>> {
    try {
        const response = await client.get<ApiResponse<Event[]>>(`/tournaments/${tournamentId}/events`);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to get tournament events',
        };
    }
}

// ================================
// Event API (competition unit)
// ================================

export async function getAllEvents(): Promise<ApiResponse<Event[]>> {
    try {
        const response = await client.get<ApiResponse<Event[]>>('/events');
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to fetch events',
        };
    }
}

export async function getEventById(id: string): Promise<ApiResponse<Event>> {
    try {
        const response = await client.get<ApiResponse<Event>>(`/events/${id}`);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Event not found',
        };
    }
}

export async function createEvent(payload: CreateEventRequest): Promise<ApiResponse<Event>> {
    try {
        const response = await client.post<ApiResponse<Event>>('/events', payload);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to create event',
        };
    }
}

export async function deleteEvent(id: string): Promise<ApiResponse<void>> {
    try {
        const response = await client.delete<ApiResponse<void>>(`/events/${id}`);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to delete event',
        };
    }
}

// ================================
// Match API
// ================================

export async function updateMatchScore(
    eventId: string,
    update: ScoreUpdateRequest
): Promise<ApiResponse<any>> {
    // Backend API: PUT /api/v1/events/{eventId}/matches/{matchId}/score
    try {
        await client.put<ApiResponse<any>>(
            `/events/${eventId}/matches/${update.matchId}/score`,
            update
        );

        // Re-fetch the event to get the full updated state (standings, bracket advancement, etc.)
        return await getEventById(eventId);

    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to update score',
        };
    }
}

// ================================
// Utilities
// ================================
export function generateShareableLink(tournamentId: string): string {
    const baseUrl = window.location.origin;
    return `${baseUrl}/view/tournament/${tournamentId}`;
}


// ================================
// Real-time (WebSockets)
// ================================
import { Client } from '@stomp/stompjs';

export function subscribeEvent(eventId: string, callback: (data: Event) => void): () => void {
    const client = new Client({
        brokerURL: import.meta.env.VITE_WS_URL || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
        client.subscribe(`/topic/event/${eventId}`, (message) => {
            if (message.body) {
                const event: Event = JSON.parse(message.body);
                callback(event);
            }
        });
    };

    client.onStompError = (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        console.error('Additional details: ' + frame.body);
    };

    client.activate();

    // Return cleanup function
    return () => {
        client.deactivate();
    };
}

// Deprecated: No longer needed with real WebSockets, but kept for compatibility if needed
export async function pollEvent(id: string): Promise<ApiResponse<Event>> {
    return getEventById(id);
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/types.ts frontend/src/api/index.ts
git commit -m "feat: swap Tournament/Event API types and functions to match new backend contract"
```

---

### Task 2: Retype `utils/tournamentLogic.ts` → `utils/eventLogic.ts`, fix `bracketUpdateLogic.ts`'s import

**Files:**
- Create: `frontend/src/utils/eventLogic.ts` (renamed from `tournamentLogic.ts`, retyped)
- Delete: `frontend/src/utils/tournamentLogic.ts`
- Modify: `frontend/src/utils/bracketUpdateLogic.ts` (one-line import path change only — this file has no direct `tournamentId`/`eventId` references, confirmed by reading it)

**Interfaces:**
- Consumes: `Match`, `Pool`, `PoolStanding`, `Team`, `Event`, `EliminationBracket`, `BracketRound` (Task 1).
- Produces: `generateId`, `generatePoolMatches(eventId, teamIds)`, `calculatePoolStandings`, `isPoolComplete`, `getTopTeamsFromPool`, `generateEliminationBracket(eventId, pools, teams, usePlaceholders)`, `advanceWinnerInBracket`, `advanceLoserToThirdPlace`, `isBracketComplete`, `shouldAdvanceToElimination(event)`, `isEventComplete(event)` — consumed by `bracketUpdateLogic.ts` (unchanged consumer) and any future page work.

- [ ] **Step 1: Create `eventLogic.ts`**

```ts
// ================================
// Event Logic Utilities
// ================================
// Handles round-robin scheduling, score calculation, and bracket progression

import { Match, Pool, PoolStanding, Team, Event, EliminationBracket, BracketRound } from '../api/types';

// Generate unique ID
export function generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

// ================================
// Round-Robin Pool Logic
// ================================

/**
 * Generate all round-robin matches for a pool of teams using Berger Table (Circle Method)
 * Handles odd numbers of teams and creates orderly rounds
 */
export function generatePoolMatches(
    eventId: string,
    teamIds: string[]
): Match[] {
    const matches: Match[] = [];
    const n = teamIds.length;

    if (n < 2) return [];

    // Clone array to avoid modifying original
    const teams = [...teamIds];

    // If odd number of teams, add 'GHOST'
    if (n % 2 !== 0) {
        teams.push('GHOST');
    }

    const totalTeams = teams.length;
    const rounds = totalTeams - 1;
    const matchesPerRound = totalTeams / 2;

    // Use a working array for rotation.
    // Index 0 is fixed (pivot), indices 1 to end rotate clockwise.
    let workingTeams = [...teams];

    for (let round = 0; round < rounds; round++) {
        for (let matchIndex = 0; matchIndex < matchesPerRound; matchIndex++) {
            const team1 = workingTeams[matchIndex];
            const team2 = workingTeams[totalTeams - 1 - matchIndex];

            // If neither team is the ghost, schedule the match
            if (team1 !== 'GHOST' && team2 !== 'GHOST') {
                matches.push({
                    id: generateId(),
                    eventId,
                    bracketRound: round + 1, // Logical round number for sorting
                    bracketPosition: matches.length + 1,
                    team1Id: team1,
                    team2Id: team2,
                    team1Score: null,
                    team2Score: null,
                    winnerId: null,
                    status: 'pending',
                    createdAt: new Date().toISOString(),
                    updatedAt: new Date().toISOString(),
                });
            }
        }

        // Rotate for next round
        // Keep index 0 fixed
        // [0, 1, 2, 3] -> [0, 3, 1, 2]
        // Take the last element and insert it at index 1
        const lastTeam = workingTeams.pop();
        if (lastTeam) {
            workingTeams.splice(1, 0, lastTeam);
        }
    }

    return matches;
}

/**
 * Calculate standings for a pool based on match results
 */
export function calculatePoolStandings(
    pool: Pool,
    teams: Team[]
): PoolStanding[] {
    const standings: Map<string, PoolStanding> = new Map();

    // Initialize standings for all teams in pool
    pool.teamIds.forEach(teamId => {
        const team = teams.find(t => t.id === teamId);
        standings.set(teamId, {
            teamId,
            teamName: team?.name || 'Unknown',
            wins: 0,
            losses: 0,
            pointsFor: 0,
            pointsAgainst: 0,
            pointDifferential: 0,
        });
    });

    // Process completed matches
    pool.matches.forEach(match => {
        if (match.status === 'completed' && match.team1Score != null && match.team2Score != null) {
            const team1Standing = standings.get(match.team1Id)!;
            const team2Standing = standings.get(match.team2Id)!;

            const score1 = match.team1Score || 0;
            const score2 = match.team2Score || 0;

            // Update points
            team1Standing.pointsFor += score1;
            team1Standing.pointsAgainst += score2;
            team2Standing.pointsFor += score2;
            team2Standing.pointsAgainst += score1;

            // Update wins/losses
            if (score1 > score2) {
                team1Standing.wins++;
                team2Standing.losses++;
            } else if (score2 > score1) {
                team2Standing.wins++;
                team1Standing.losses++;
            }
        }
    });

    // Calculate point differentials and sort
    const standingsArray = Array.from(standings.values()).map(s => ({
        ...s,
        pointDifferential: s.pointsFor - s.pointsAgainst,
    }));

    // Sort by: 1) Wins (desc), 2) Point differential (desc), 3) Points for (desc)
    standingsArray.sort((a, b) => {
        if (b.wins !== a.wins) return b.wins - a.wins;
        if (b.pointDifferential !== a.pointDifferential) return b.pointDifferential - a.pointDifferential;
        return b.pointsFor - a.pointsFor;
    });

    return standingsArray;
}

/**
 * Check if all pool matches are completed
 */
export function isPoolComplete(pool: Pool): boolean {
    return pool.matches.every(match => match.status === 'completed');
}

/**
 * Get top N teams from pool standings
 */
export function getTopTeamsFromPool(pool: Pool, teams: Team[], count: number = 2): string[] {
    const standings = calculatePoolStandings(pool, teams);
    return standings.slice(0, count).map(s => s.teamId);
}

// ================================
// Elimination Bracket Logic
// ================================

/**
 * Generate elimination bracket from pool winners
 * Assumes 4 teams (2 pools x 2 teams each) for semifinals -> finals
 */
export function generateEliminationBracket(
    eventId: string,
    pools: Pool[],
    teams: Team[],
    usePlaceholders: boolean = false
): EliminationBracket {
    const rounds: BracketRound[] = [];
    const advancingTeams: { teamId: string, poolIndex: number, rank: number }[] = [];

    // 1. Collect top 2 teams from each pool
    pools.forEach((pool, poolIndex) => {
        const topTeams = getTopTeamsFromPool(pool, teams, 2);
        topTeams.forEach((teamId, rankIndex) => {
            advancingTeams.push({
                teamId,
                poolIndex,
                rank: rankIndex + 1 // 1 for 1st, 2 for 2nd
            });
        });
    });

    // 2. Pair teams (Standard: Pool A #1 vs Pool B #2, etc.)
    // We will pair Pool(i) #1  vs Pool(i+1) #2
    // If only 1 pool, it's 1 vs 2 (Finals).

    const initialMatches: Match[] = [];
    const poolCount = pools.length;

    if (poolCount === 1) {
        // Single pool fallback: 1 vs 2 (Finals).
        let team1Id = '';
        let team2Id = '';

        if (!usePlaceholders) {
            const top2 = advancingTeams.filter(t => t.rank <= 2);
            team1Id = top2.find(t => t.rank === 1)?.teamId || '';
            team2Id = top2.find(t => t.rank === 2)?.teamId || '';
        }

        initialMatches.push({
            id: generateId(),
            eventId,
            bracketRound: 1,
            bracketPosition: 1,
            team1Id,
            team2Id,
            team1Score: null,
            team2Score: null,
            winnerId: null,
            status: 'pending',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
        });

    } else {
        // Multiple pools: 1st vs 2nd crossover
        for (let i = 0; i < poolCount; i++) {
            let team1Id = '';
            let team2Id = '';

            if (!usePlaceholders) {
                const pool1Index = i;
                const pool2Index = (i + 1) % poolCount; // Wrap around for the last pool

                const team1 = advancingTeams.find(t => t.poolIndex === pool1Index && t.rank === 1);
                const team2 = advancingTeams.find(t => t.poolIndex === pool2Index && t.rank === 2);

                team1Id = team1?.teamId || '';
                team2Id = team2?.teamId || '';
            }

            initialMatches.push({
                id: generateId(),
                eventId,
                bracketRound: 1,
                bracketPosition: i + 1,
                team1Id,
                team2Id,
                team1Score: null,
                team2Score: null,
                winnerId: null,
                status: 'pending',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            });
        }
    }

    // 3. Construct bracket rounds
    const totalMatches = initialMatches.length;
    let currentRoundMatches = initialMatches;
    let roundNumber = 1;

    let roundName = 'Elimination Round';
    if (totalMatches === 1) roundName = 'Finals';
    else if (totalMatches === 2) roundName = 'Semifinals';
    else if (totalMatches === 4) roundName = 'Quarterfinals';

    rounds.push({
        roundNumber: roundNumber,
        name: roundName,
        matches: currentRoundMatches
    });

    // Generate subsequent placeholder rounds until Finals
    let matchCount = totalMatches;
    while (matchCount > 1) {
        roundNumber++;
        matchCount = Math.ceil(matchCount / 2);

        let subRoundName = 'Elimination Round';
        if (matchCount === 1) subRoundName = 'Finals';
        else if (matchCount === 2) subRoundName = 'Semifinals';

        const nextRoundMatches: Match[] = [];
        for (let i = 0; i < matchCount; i++) {
            nextRoundMatches.push({
                id: generateId(),
                eventId,
                bracketRound: roundNumber,
                bracketPosition: i + 1,
                team1Id: '', // TBD
                team2Id: '', // TBD
                team1Score: null,
                team2Score: null,
                winnerId: null,
                status: 'pending',
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            });
        }

        rounds.push({
            roundNumber: roundNumber,
            name: subRoundName,
            matches: nextRoundMatches
        });
    }

    return {
        eventId,
        rounds,
        champion: null,
    };
}

/**
 * Advance winner to next round in elimination bracket
 * Also handles bye matches (when odd number of matches means a team auto-advances)
 */
export function advanceWinnerInBracket(
    bracket: EliminationBracket,
    completedMatch: Match
): EliminationBracket {
    if (!completedMatch.winnerId || completedMatch.bracketRound === undefined) {
        return bracket;
    }

    const currentRoundIndex = bracket.rounds.findIndex(
        r => r.roundNumber === completedMatch.bracketRound
    );

    if (currentRoundIndex === -1 || currentRoundIndex === bracket.rounds.length - 1) {
        // This was the final match
        if (currentRoundIndex === bracket.rounds.length - 1) {
            return {
                ...bracket,
                champion: completedMatch.winnerId,
            };
        }
        return bracket;
    }

    // Find next round and update the appropriate match
    const currentRound = bracket.rounds[currentRoundIndex];
    const nextRound = bracket.rounds[currentRoundIndex + 1];
    const nextMatchIndex = Math.floor((completedMatch.bracketPosition! - 1) / 2);

    if (nextRound.matches[nextMatchIndex]) {
        const nextMatch = { ...nextRound.matches[nextMatchIndex] };

        // First match winner goes to team1, second match winner goes to team2
        if (completedMatch.bracketPosition! % 2 === 1) {
            nextMatch.team1Id = completedMatch.winnerId;
        } else {
            nextMatch.team2Id = completedMatch.winnerId;
        }

        // Check if this is a "bye match" - only one feeder expected
        const currentRoundMatchCount = currentRound.matches.length;
        const isLastMatchInNextRound = nextMatchIndex === nextRound.matches.length - 1;
        const currentRoundIsOdd = currentRoundMatchCount % 2 === 1;

        if (currentRoundIsOdd && isLastMatchInNextRound && completedMatch.bracketPosition === currentRoundMatchCount) {
            // This is a bye match - the team from the odd match auto-advances
            nextMatch.team2Id = ''; // No opponent
            nextMatch.winnerId = completedMatch.winnerId;
            nextMatch.status = 'completed';
            nextMatch.team1Score = 0; // Bye
            nextMatch.team2Score = 0;
        }

        nextRound.matches[nextMatchIndex] = nextMatch;

        // If the nextMatch is now complete (bye), recursively advance
        if (nextMatch.status === 'completed' && nextMatch.winnerId) {
            const updatedBracket = { ...bracket };
            return advanceWinnerInBracket(updatedBracket, nextMatch);
        }
    }

    return { ...bracket };
}

/**
 * Advance loser from semifinal to third-place match
 * Handles auto-3rd place when only 1 semifinal loser exists
 */
export function advanceLoserToThirdPlace(
    bracket: EliminationBracket,
    completedMatch: Match,
    hasThirdPlaceMatch: boolean = false
): EliminationBracket {
    if (!hasThirdPlaceMatch || !completedMatch.winnerId || completedMatch.bracketRound === undefined) {
        return bracket;
    }

    // Find the semifinals round (the round before finals)
    const finalsRoundIndex = bracket.rounds.findIndex(r => r.name === 'Finals');
    if (finalsRoundIndex === -1 || finalsRoundIndex === 0) {
        return bracket; // No semifinals if finals is first or not found
    }

    const semifinalsRound = bracket.rounds[finalsRoundIndex - 1];

    // Only process if this match is in the semifinals
    if (completedMatch.bracketRound !== semifinalsRound.roundNumber) {
        return bracket;
    }

    // Get the loser
    const loserId = completedMatch.team1Id === completedMatch.winnerId
        ? completedMatch.team2Id
        : completedMatch.team1Id;

    if (!loserId) {
        return bracket;
    }

    const updatedBracket = { ...bracket };
    const semifinalMatches = semifinalsRound.matches;
    const totalSemifinalsCount = semifinalMatches.length;

    // If only 1 semifinal match exists, the loser auto-qualifies as 3rd place
    if (totalSemifinalsCount === 1) {
        updatedBracket.thirdPlaceTeamId = loserId;
        updatedBracket.thirdPlaceMatch = null;
        return updatedBracket;
    }

    // Initialize third-place match if it doesn't exist
    if (!updatedBracket.thirdPlaceMatch) {
        updatedBracket.thirdPlaceMatch = {
            id: generateId(),
            eventId: bracket.eventId,
            bracketRound: finalsRoundIndex + 1, // Same round as finals conceptually
            bracketPosition: 0, // Special position for 3rd place match
            team1Id: '',
            team2Id: '',
            team1Score: null,
            team2Score: null,
            winnerId: null,
            status: 'pending',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
        };
    }

    // Assign loser to third-place match slot
    const thirdPlaceMatch = { ...updatedBracket.thirdPlaceMatch };
    if (!thirdPlaceMatch.team1Id) {
        thirdPlaceMatch.team1Id = loserId;
    } else if (!thirdPlaceMatch.team2Id && thirdPlaceMatch.team1Id !== loserId) {
        thirdPlaceMatch.team2Id = loserId;
    }
    updatedBracket.thirdPlaceMatch = thirdPlaceMatch;

    return updatedBracket;
}

/**
 * Check if all elimination matches are complete
 */
export function isBracketComplete(bracket: EliminationBracket): boolean {
    return bracket.rounds.every(round =>
        round.matches.every(match => match.status === 'completed')
    );
}

// ================================
// Event State Transitions
// ================================

/**
 * Determine if event should advance from pool play to elimination
 */
export function shouldAdvanceToElimination(event: Event): boolean {
    if (event.status !== 'pool_play') return false;
    return event.pools.every(pool => isPoolComplete(pool));
}

/**
 * Determine if event is complete
 */
export function isEventComplete(event: Event): boolean {
    if (!event.eliminationBracket) return false;
    return event.eliminationBracket.champion !== null;
}
```

- [ ] **Step 2: Delete `tournamentLogic.ts`**

Delete `frontend/src/utils/tournamentLogic.ts`.

- [ ] **Step 3: Fix `bracketUpdateLogic.ts`'s import (only change needed in this file)**

Change line 3 from:
```ts
import { isPoolComplete, getTopTeamsFromPool } from './tournamentLogic';
```
to:
```ts
import { isPoolComplete, getTopTeamsFromPool } from './eventLogic';
```
Nothing else in `bracketUpdateLogic.ts` changes — it operates on `Pool`/`Team`/`EliminationBracket` generically and never reads a `tournamentId`/`eventId` field directly.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/utils/
git commit -m "feat: rename tournamentLogic.ts to eventLogic.ts, retype Tournament refs to Event"
```

---

### Task 3: Fix `EliminationBracket.tsx` and `Header.tsx`

**Files:**
- Modify: `frontend/src/components/EliminationBracket.tsx` (one-line fix)
- Modify: `frontend/src/components/Header.tsx` (nav link + label)

**Interfaces:**
- Consumes: `EliminationBracket`, `Team` (Task 1).

- [ ] **Step 1: Fix `EliminationBracket.tsx`**

Change (inside the `thirdPlaceData` `useMemo`, where a virtual third-place match is constructed):
```ts
const match = {
    id: existingMatch?.id || 'third-place-match',
    tournamentId: bracket.tournamentId,
```
to:
```ts
const match = {
    id: existingMatch?.id || 'third-place-match',
    eventId: bracket.eventId,
```
Nothing else in this file changes — `bracket.tournamentId` was the only reference to the renamed field.

- [ ] **Step 2: Update `Header.tsx`'s nav link**

Change:
```tsx
<Link to="/events" className="nav-link nav-link-events">
    <Calendar size={16} />
    Events
</Link>
```
to:
```tsx
<Link to="/tournaments" className="nav-link nav-link-events">
    <Calendar size={16} />
    Tournaments
</Link>
```
(The `nav-link-events` class name stays literal — CSS class names are never renamed per Global Constraints. Only the `href` and visible label change.)

- [ ] **Step 3: Compile check**

Run: `cd frontend && npx tsc -b --noEmit 2>&1 | grep -v "pages/\|AdminDashboard"` (or just run `npx tsc -b --noEmit` and confirm the only errors reported are in files not yet updated by this task — pages still import from the old `EventsPage`/`TournamentSetup` etc. paths and reference old type field names; that's expected at this stage).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/EliminationBracket.tsx frontend/src/components/Header.tsx
git commit -m "fix: retype EliminationBracket third-place match construction, update Header nav to Tournaments"
```

---

### Task 4: Rewrite `App.tsx`

**Files:**
- Modify: `frontend/src/App.tsx` (full rewrite)

**Interfaces:**
- Consumes: `TournamentsPage`, `TournamentDetailPage`, `EventSetup`, `TournamentViewerPage` — these page components are produced by Tasks 5-8 and don't exist yet at this point in the plan. This task's compile check will show missing-module errors for those 4 imports until Tasks 5-8 land; that's expected (same interdependency pattern as the rest of this plan).

- [ ] **Step 1: Rewrite `App.tsx`**

```tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LandingPage } from './pages/LandingPage';
import { EventSetup } from './pages/EventSetup';
import { AdminDashboard } from './pages/AdminDashboard';
import { TournamentViewerPage } from './pages/TournamentViewerPage';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { TournamentsPage } from './pages/TournamentsPage';
import { TournamentDetailPage } from './pages/TournamentDetailPage';
import './index.css';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public landing page - redirects to admin if logged in */}
          <Route path="/" element={<LandingPage />} />

          {/* Auth routes */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />

          {/* Protected admin routes */}
          <Route path="/admin" element={
            <ProtectedRoute>
              <AdminDashboard />
            </ProtectedRoute>
          } />
          <Route path="/setup" element={
            <ProtectedRoute>
              <EventSetup />
            </ProtectedRoute>
          } />

          {/* Tournaments routes */}
          <Route path="/tournaments" element={
            <ProtectedRoute>
              <TournamentsPage />
            </ProtectedRoute>
          } />
          <Route path="/tournaments/:tournamentId" element={
            <ProtectedRoute>
              <TournamentDetailPage />
            </ProtectedRoute>
          } />

          {/* Public tournament viewer route - no auth required */}
          <Route path="/view/tournament/:tournamentId" element={<TournamentViewerPage />} />

          {/* Fallback to landing */}
          <Route path="*" element={<LandingPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/App.tsx
git commit -m "feat: swap App.tsx routes to /tournaments and /view/tournament"
```

---

### Task 5: `TournamentsPage.tsx` (new, was `EventsPage.tsx`)

**Files:**
- Create: `frontend/src/pages/TournamentsPage.tsx`
- Create: `frontend/src/pages/TournamentsPage.css` (copy `EventsPage.css` content verbatim — no changes, class names stay literal)
- Delete: `frontend/src/pages/EventsPage.tsx`, `frontend/src/pages/EventsPage.css`

**Interfaces:**
- Consumes: `getAllTournaments`, `createTournament` (Task 1), `Tournament` (Task 1), `Header` (unchanged).
- Produces: `TournamentsPage` component — consumed by `App.tsx` (Task 4) and linked to from `TournamentDetailPage`/`AdminDashboard`/`Header`.

- [ ] **Step 1: Create `TournamentsPage.tsx`**

```tsx
import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Calendar, Plus, Trophy } from 'lucide-react';
import { Tournament } from '../api/types';
import { getAllTournaments, createTournament } from '../api';
import { Header } from '../components/Header';
import './TournamentsPage.css';

export function TournamentsPage() {
    const navigate = useNavigate();
    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newTournamentName, setNewTournamentName] = useState('');
    const [newTournamentDescription, setNewTournamentDescription] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setIsLoading(true);
        const tournamentsRes = await getAllTournaments();

        if (tournamentsRes.success && tournamentsRes.data) {
            setTournaments(tournamentsRes.data);
        }
        setIsLoading(false);
    };

    const handleCreateTournament = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        if (!newTournamentName.trim()) {
            setError('Tournament name is required');
            return;
        }

        const result = await createTournament({
            name: newTournamentName.trim(),
            description: newTournamentDescription.trim() || undefined,
        });

        if (result.success && result.data) {
            setTournaments(prev => [...prev, result.data!]);
            setShowCreateModal(false);
            setNewTournamentName('');
            setNewTournamentDescription('');
            // Navigate to the new tournament
            navigate(`/tournaments/${result.data.id}`);
        } else {
            setError(result.error || 'Failed to create tournament');
        }
    };


    const getEventCount = (tournament: Tournament) => {
        return tournament.eventIds?.length || 0;
    };



    if (isLoading) {
        return (
            <div className="events-page">
                <Header />
                <div className="loading-state">Loading tournaments...</div>
            </div>
        );
    }

    return (
        <div className="events-page">
            <Header />

            <main className="events-content">
                <div className="events-header">
                    <div className="header-text">
                        <h1>Tournaments</h1>
                        <p>Manage your tournaments and events</p>
                    </div>
                    <button
                        className="create-event-btn"
                        onClick={() => setShowCreateModal(true)}
                    >
                        <Plus size={20} />
                        Create Tournament
                    </button>
                </div>

                {tournaments.length === 0 ? (
                    <div className="empty-state">
                        <Calendar size={64} />
                        <h2>No Tournaments Yet</h2>
                        <p>Create your first tournament to organize multiple events together.</p>
                        <button
                            className="create-event-btn-large"
                            onClick={() => setShowCreateModal(true)}
                        >
                            Create Your First Tournament
                        </button>
                    </div>
                ) : (
                    <>
                        {/* Tournaments List */}
                        <section className="events-section">
                            <h2 className="section-title">
                                <Calendar size={20} />
                                Your Tournaments
                            </h2>
                            <div className="events-grid">
                                {tournaments.map(tournament => (
                                    <div key={tournament.id} className="event-card">
                                        <Link to={`/tournaments/${tournament.id}`} className="event-card-content">
                                            <div className="event-icon">
                                                <Calendar size={24} />
                                            </div>
                                            <div className="event-info">
                                                <h3>{tournament.name}</h3>
                                                {tournament.description && (
                                                    <p className="event-description">{tournament.description}</p>
                                                )}
                                                <div className="event-meta">
                                                    <span className="tournament-count">
                                                        <Trophy size={14} />
                                                        {getEventCount(tournament)} event{getEventCount(tournament) !== 1 ? 's' : ''}
                                                    </span>
                                                </div>
                                            </div>
                                        </Link>
                                    </div>
                                ))}
                            </div>
                        </section>
                    </>
                )}
            </main>

            {/* Create Tournament Modal */}
            {showCreateModal && (
                <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <h2>Create New Tournament</h2>
                        <form onSubmit={handleCreateTournament}>
                            <div className="form-group">
                                <label htmlFor="tournamentName">Tournament Name *</label>
                                <input
                                    id="tournamentName"
                                    type="text"
                                    value={newTournamentName}
                                    onChange={e => setNewTournamentName(e.target.value)}
                                    placeholder="e.g., Summer Pickleball Championship 2026"
                                    autoFocus
                                />
                            </div>
                            <div className="form-group">
                                <label htmlFor="tournamentDescription">Description (optional)</label>
                                <textarea
                                    id="tournamentDescription"
                                    value={newTournamentDescription}
                                    onChange={e => setNewTournamentDescription(e.target.value)}
                                    placeholder="Describe your tournament..."
                                    rows={3}
                                />
                            </div>
                            {error && <p className="error-message">{error}</p>}
                            <div className="modal-actions">
                                <button
                                    type="button"
                                    className="btn-secondary"
                                    onClick={() => setShowCreateModal(false)}
                                >
                                    Cancel
                                </button>
                                <button type="submit" className="btn-primary">
                                    Create Tournament
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
```

Note: root `className`s (`events-page`, `events-content`, `events-header`, etc.) stay literal — copied from `EventsPage.css` into `TournamentsPage.css` unchanged, per Global Constraints (no CSS renaming).

- [ ] **Step 2: Copy `EventsPage.css` to `TournamentsPage.css`**

Read `frontend/src/pages/EventsPage.css` and write its exact content, unchanged, to `frontend/src/pages/TournamentsPage.css`.

- [ ] **Step 3: Delete old files**

Delete `frontend/src/pages/EventsPage.tsx` and `frontend/src/pages/EventsPage.css`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/TournamentsPage.tsx frontend/src/pages/TournamentsPage.css
git rm frontend/src/pages/EventsPage.tsx frontend/src/pages/EventsPage.css
git commit -m "feat: rename EventsPage to TournamentsPage, retype to new Tournament shape"
```

---

### Task 6: `TournamentDetailPage.tsx` (new, was `EventDetailPage.tsx`)

**Files:**
- Create: `frontend/src/pages/TournamentDetailPage.tsx`
- Create: `frontend/src/pages/TournamentDetailPage.css` (copy `EventDetailPage.css` verbatim)
- Delete: `frontend/src/pages/EventDetailPage.tsx`, `frontend/src/pages/EventDetailPage.css`

**Interfaces:**
- Consumes: `getTournamentById`, `updateTournament`, `getTournamentEvents`, `deleteEvent`, `deleteTournament` (Task 1), `Tournament`, `Event` (Task 1).
- Produces: `TournamentDetailPage` component — consumed by `App.tsx` (Task 4).

- [ ] **Step 1: Create `TournamentDetailPage.tsx`**

```tsx
import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Calendar, Trophy, Plus, Trash2, Edit2, X, Check } from 'lucide-react';
import { Tournament, Event } from '../api/types';
import { getTournamentById, updateTournament, getTournamentEvents, deleteEvent, deleteTournament } from '../api';
import { Header } from '../components/Header';
import './TournamentDetailPage.css';

export function TournamentDetailPage() {
    const { tournamentId } = useParams<{ tournamentId: string }>();
    const navigate = useNavigate();

    const [tournament, setTournament] = useState<Tournament | null>(null);
    const [events, setEvents] = useState<Event[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isEditing, setIsEditing] = useState(false);
    const [editName, setEditName] = useState('');
    const [editDescription, setEditDescription] = useState('');

    useEffect(() => {
        if (tournamentId) {
            loadTournamentData();
        }
    }, [tournamentId]);

    const loadTournamentData = async () => {
        if (!tournamentId) return;

        setIsLoading(true);
        const [tournamentRes, eventsRes] = await Promise.all([
            getTournamentById(tournamentId),
            getTournamentEvents(tournamentId)
        ]);

        if (tournamentRes.success && tournamentRes.data) {
            setTournament(tournamentRes.data);
            setEditName(tournamentRes.data.name);
            setEditDescription(tournamentRes.data.description || '');
        }
        if (eventsRes.success && eventsRes.data) {
            setEvents(eventsRes.data);
        }
        setIsLoading(false);
    };

    const handleSaveEdit = async () => {
        if (!tournamentId || !editName.trim()) return;

        const result = await updateTournament(tournamentId, {
            name: editName.trim(),
            description: editDescription.trim() || undefined,
        });

        if (result.success && result.data) {
            setTournament(result.data);
            setIsEditing(false);
        }
    };

    const handleDeleteEventFromTournament = async (eventId: string) => {
        if (!confirm('Delete this event? This cannot be undone.')) return;

        const result = await deleteEvent(eventId);
        if (result.success) {
            setEvents(prev => prev.filter(e => e.id !== eventId));
        }
    };

    const handleDeleteTournament = async () => {
        if (!tournamentId) return;
        if (!confirm('Delete this tournament?')) return;

        const result = await deleteTournament(tournamentId);
        if (result.success) {
            navigate('/tournaments');
        }
    };

    const getStatusColor = (status: Event['status']) => {
        switch (status) {
            case 'pool_play': return 'status-pool';
            case 'elimination': return 'status-elimination';
            case 'completed': return 'status-completed';
            default: return 'status-setup';
        }
    };

    if (isLoading) {
        return (
            <div className="event-detail-page">
                <Header />
                <div className="loading-state">Loading tournament...</div>
            </div>
        );
    }

    if (!tournament) {
        return (
            <div className="event-detail-page">
                <Header />
                <div className="error-state">
                    <h2>Tournament Not Found</h2>
                    <Link to="/tournaments" className="back-link">← Back to Tournaments</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="event-detail-page">
            <Header />

            <main className="event-detail-content">
                {/* Back Navigation */}
                <Link to="/tournaments" className="back-nav">
                    <ArrowLeft size={20} />
                    Back to Tournaments
                </Link>

                {/* Tournament Header */}
                <div className="event-header-card">
                    <div className="event-header-icon">
                        <Calendar size={32} />
                    </div>

                    <div className="event-header-content">
                        {isEditing ? (
                            <div className="edit-form">
                                <input
                                    type="text"
                                    value={editName}
                                    onChange={e => setEditName(e.target.value)}
                                    className="edit-name-input"
                                    placeholder="Tournament name"
                                    autoFocus
                                />
                                <textarea
                                    value={editDescription}
                                    onChange={e => setEditDescription(e.target.value)}
                                    className="edit-description-input"
                                    placeholder="Description (optional)"
                                    rows={2}
                                />
                                <div className="edit-actions">
                                    <button className="btn-icon btn-cancel" onClick={() => setIsEditing(false)}>
                                        <X size={18} />
                                    </button>
                                    <button className="btn-icon btn-save" onClick={handleSaveEdit}>
                                        <Check size={18} />
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <>
                                <h1>{tournament.name}</h1>
                                {tournament.description && <p className="event-description">{tournament.description}</p>}
                                <div className="event-meta">
                                    <span className="tournament-count">
                                        <Trophy size={16} />
                                        {events.length} event{events.length !== 1 ? 's' : ''}
                                    </span>
                                </div>
                            </>
                        )}
                    </div>

                    {!isEditing && (
                        <div className="event-header-actions">
                            <button className="btn-icon" onClick={() => setIsEditing(true)}>
                                <Edit2 size={18} />
                            </button>
                            <button className="btn-icon btn-danger" onClick={handleDeleteTournament}>
                                <Trash2 size={18} />
                            </button>
                        </div>
                    )}
                </div>

                {/* Events Section */}
                <section className="tournaments-section">
                    <div className="section-header">
                        <h2>
                            <Trophy size={20} />
                            Events
                        </h2>
                        <Link to={`/setup?tournamentId=${tournamentId}`} className="add-tournament-btn">
                            <Plus size={18} />
                            Add Event
                        </Link>
                    </div>

                    {events.length === 0 ? (
                        <div className="empty-tournaments">
                            <Trophy size={48} />
                            <h3>No Events Yet</h3>
                            <p>Create your first event for this tournament</p>
                            <Link to={`/setup?tournamentId=${tournamentId}`} className="create-tournament-btn">
                                Create Event
                            </Link>
                        </div>
                    ) : (
                        <div className="tournaments-grid">
                            {events.map(event => (
                                <div key={event.id} className="tournament-card">
                                    <Link to={`/admin?id=${event.id}`} className="tournament-card-content">
                                        <div className="tournament-info">
                                            <h3>{event.name}</h3>
                                            <div className="tournament-meta">
                                                <span className={`status-badge ${getStatusColor(event.status)}`}>
                                                    {event.status.replace('_', ' ')}
                                                </span>
                                                <span className="team-count">
                                                    {event.teams.length} teams
                                                </span>
                                                <span className="pool-count">
                                                    {event.pools.length} pools
                                                </span>
                                            </div>
                                        </div>
                                    </Link>
                                    <button
                                        className="remove-tournament-btn"
                                        onClick={() => handleDeleteEventFromTournament(event.id)}
                                        title="Delete event"
                                    >
                                        <Trash2 size={16} />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </section>
            </main>
        </div>
    );
}
```

- [ ] **Step 2: Copy `EventDetailPage.css` to `TournamentDetailPage.css`**

Read `frontend/src/pages/EventDetailPage.css` and write its exact content, unchanged, to `frontend/src/pages/TournamentDetailPage.css`.

- [ ] **Step 3: Delete old files**

Delete `frontend/src/pages/EventDetailPage.tsx` and `frontend/src/pages/EventDetailPage.css`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/TournamentDetailPage.tsx frontend/src/pages/TournamentDetailPage.css
git rm frontend/src/pages/EventDetailPage.tsx frontend/src/pages/EventDetailPage.css
git commit -m "feat: rename EventDetailPage to TournamentDetailPage, delete-event replaces remove-tournament-from-event"
```

---

### Task 7: `EventSetup.tsx` (new, was `TournamentSetup.tsx`)

**Files:**
- Create: `frontend/src/pages/EventSetup.tsx`
- Create: `frontend/src/pages/EventSetup.css` (copy `TournamentSetup.css` verbatim)
- Delete: `frontend/src/pages/TournamentSetup.tsx`, `frontend/src/pages/TournamentSetup.css`

**Interfaces:**
- Consumes: `createEvent` (Task 1).
- Produces: `EventSetup` component — consumed by `App.tsx` (Task 4).

- [ ] **Step 1: Create `EventSetup.tsx`**

Note the new guard: `tournamentId` is required by `CreateEventRequest` (Task 1) — TypeScript will not compile a call passing `undefined`. Unlike the old page (which tolerated a missing `eventId` since it was optional), this page shows an error state if reached without a `tournamentId` instead of silently submitting an invalid request. In practice this route is only ever reached from `TournamentDetailPage`'s "Add Event" link, which always supplies one.

```tsx
import { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { createEvent } from '../api';
import { ChevronLeft, Plus, Trash2, Users } from 'lucide-react';
import './EventSetup.css';

export function EventSetup() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const tournamentId = searchParams.get('tournamentId');

    const [eventName, setEventName] = useState('');

    // Initial state: 1 pool with 2 empty slots
    const [pools, setPools] = useState<{ name: string; teams: string[] }[]>([
        { name: 'Pool A', teams: ['', ''] }
    ]);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const addPool = () => {
        const nextPoolName = `Pool ${String.fromCharCode(65 + pools.length)}`;
        setPools([
            ...pools,
            { name: nextPoolName, teams: ['', ''] } // Start with 2 empty slots
        ]);
    };

    const removePool = (index: number) => {
        if (pools.length > 1) {
            const newPools = pools.filter((_, i) => i !== index);
            const renumbered = newPools.map((pool, i) => ({
                ...pool,
                name: `Pool ${String.fromCharCode(65 + i)}`
            }));
            setPools(renumbered);
        }
    };

    const updateTeamName = (poolIndex: number, teamIndex: number, name: string) => {
        const newPools = [...pools];
        newPools[poolIndex].teams[teamIndex] = name;
        setPools(newPools);
    };

    const addTeamSlot = (poolIndex: number) => {
        const newPools = [...pools];
        newPools[poolIndex].teams.push('');
        setPools(newPools);
    };

    const removeTeamSlot = (poolIndex: number, teamIndex: number) => {
        const newPools = [...pools];
        if (newPools[poolIndex].teams.length > 2) {
            newPools[poolIndex].teams.splice(teamIndex, 1);
            setPools(newPools);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        if (!tournamentId) {
            setError('No tournament selected — go back and add an event from a tournament page');
            return;
        }

        // Validation
        if (!eventName.trim()) {
            setError('Please enter an event name');
            return;
        }

        // Validate each pool
        for (const pool of pools) {
            const validTeams = pool.teams.filter(t => t.trim());
            if (validTeams.length < 2) {
                setError(`${pool.name} needs at least 2 teams`);
                return;
            }
            if (new Set(validTeams).size !== validTeams.length) {
                setError(`Duplicate team names found in ${pool.name}`);
                return;
            }
        }

        setLoading(true);

        try {
            const response = await createEvent({
                name: eventName,
                tournamentId,
                pools: pools.map(pool => ({
                    name: pool.name,
                    teamNames: pool.teams.filter(t => t.trim())
                }))
            });

            if (response.success) {
                navigate(`/tournaments/${tournamentId}`);
            } else {
                setError(response.error || 'Failed to create event');
            }
        } catch (err) {
            setError('An unexpected error occurred');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleBack = () => {
        if (tournamentId) {
            navigate(`/tournaments/${tournamentId}`);
        } else {
            navigate('/tournaments');
        }
    };

    if (!tournamentId) {
        return (
            <div className="setup-container">
                <header className="setup-header">
                    <button onClick={() => navigate('/tournaments')} className="back-button">
                        <ChevronLeft size={20} />
                        Back
                    </button>
                    <h1>No Tournament Selected</h1>
                </header>
                <div className="setup-content">
                    <p>An event must be created from inside a tournament. <Link to="/tournaments">Go to Tournaments</Link> and use "Add Event" from a tournament page.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="setup-container">
            <header className="setup-header">
                <button onClick={handleBack} className="back-button">
                    <ChevronLeft size={20} />
                    Back
                </button>
                <h1>Create New Event</h1>
            </header>

            <div className="setup-content">
                <form onSubmit={handleSubmit} className="setup-form">
                    <div className="form-section">
                        <div className="section-header">
                            <h2>Event Details</h2>
                        </div>
                        <div className="input-group">
                            <label htmlFor="name">Event Name</label>
                            <input
                                type="text"
                                id="name"
                                value={eventName}
                                onChange={(e) => setEventName(e.target.value)}
                                placeholder="e.g. Summer Pickleball Open 2024"
                                disabled={loading}
                            />
                        </div>
                    </div>

                    <div className="pools-grid">
                        {pools.map((pool, poolIndex) => (
                            <div key={poolIndex} className="form-section pool-section">
                                <div className="pool-header">
                                    <h3>{pool.name}</h3>
                                    <div className="pool-header-actions">
                                        <span className="team-count">{pool.teams.filter(t => t.trim()).length} Teams</span>
                                        {pools.length > 1 && (
                                            <button
                                                type="button"
                                                onClick={() => removePool(poolIndex)}
                                                className="remove-pool-btn"
                                                title="Remove Pool"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        )}
                                    </div>
                                </div>

                                <div className="teams-list">
                                    {pool.teams.map((team, teamIndex) => (
                                        <div key={teamIndex} className="team-input-row">
                                            <span className="team-number">#{teamIndex + 1}</span>
                                            <input
                                                type="text"
                                                value={team}
                                                onChange={(e) => updateTeamName(poolIndex, teamIndex, e.target.value)}
                                                placeholder={`Team Name`}
                                                disabled={loading}
                                            />
                                            {pool.teams.length > 2 && (
                                                <button
                                                    type="button"
                                                    onClick={() => removeTeamSlot(poolIndex, teamIndex)}
                                                    className="remove-team-btn"
                                                    disabled={loading}
                                                >
                                                    <Trash2 size={18} />
                                                </button>
                                            )}
                                        </div>
                                    ))}
                                </div>

                                <button
                                    type="button"
                                    onClick={() => addTeamSlot(poolIndex)}
                                    className="add-team-btn"
                                    disabled={loading}
                                >
                                    <Plus size={18} />
                                    Add Team Slot
                                </button>
                            </div>
                        ))}

                        {/* Add Pool Button */}
                        <div className="add-pool-section">
                            <button
                                type="button"
                                onClick={addPool}
                                className="add-pool-btn"
                                disabled={loading}
                            >
                                <Plus size={24} />
                                <span>Add Another Pool</span>
                            </button>
                        </div>
                    </div>

                    {error && <div className="error-message">{error}</div>}

                    <div className="form-actions sticky-actions">
                        <button
                            type="submit"
                            className="create-btn"
                            disabled={loading}
                        >
                            {loading ? 'Creating...' : 'Create Event'}
                        </button>
                    </div>
                </form>

                <div className="setup-info">
                    <div className="info-card">
                        <Users size={24} />
                        <h3>Builder Guide</h3>
                        <p>
                            Construct your event by adding pools and teams.
                        </p>
                        <ul>
                            <li><strong>Pools:</strong> Add as many as needed (A, B, C...)</li>
                            <li><strong>Teams:</strong> Minimum 2 per pool.</li>
                            <li><strong>Scoring:</strong> Round-robin within pools.</li>
                            <li><strong>Playoffs:</strong> Top 2 from EACH pool advance.</li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    );
}
```

- [ ] **Step 2: Copy `TournamentSetup.css` to `EventSetup.css`**

Read `frontend/src/pages/TournamentSetup.css` and write its exact content, unchanged, to `frontend/src/pages/EventSetup.css`.

- [ ] **Step 3: Delete old files**

Delete `frontend/src/pages/TournamentSetup.tsx` and `frontend/src/pages/TournamentSetup.css`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/EventSetup.tsx frontend/src/pages/EventSetup.css
git rm frontend/src/pages/TournamentSetup.tsx frontend/src/pages/TournamentSetup.css
git commit -m "feat: rename TournamentSetup to EventSetup, require tournamentId, guard against missing one"
```

---

### Task 8: `TournamentViewerPage.tsx` (new, was `EventViewerPage.tsx`)

**Files:**
- Create: `frontend/src/pages/TournamentViewerPage.tsx`
- Create: `frontend/src/pages/TournamentViewerPage.css` (copy `EventViewerPage.css` verbatim)
- Delete: `frontend/src/pages/EventViewerPage.tsx`, `frontend/src/pages/EventViewerPage.css`

**Interfaces:**
- Consumes: `getTournamentById`, `getTournamentEvents`, `subscribeEvent`, `pollEvent` (Task 1), `Tournament`, `Event` (Task 1), `Header`, `PoolStandings`, `MatchCard`, `EliminationBracket`, `TournamentTabs` (unchanged components).
- Produces: `TournamentViewerPage` component — consumed by `App.tsx` (Task 4).

- [ ] **Step 1: Create `TournamentViewerPage.tsx`**

```tsx
import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { Header } from '../components/Header';
import { PoolStandings } from '../components/PoolStandings';
import { MatchCard } from '../components/MatchCard';
import { EliminationBracket } from '../components/EliminationBracket';
import { TournamentTabs } from '../components/TournamentTabs';
import { getTournamentById, getTournamentEvents, subscribeEvent, pollEvent } from '../api';
import { Event, Tournament } from '../api/types';
import { RefreshCw, Wifi, ChevronDown, Trophy } from 'lucide-react';
import './TournamentViewerPage.css';

export function TournamentViewerPage() {
    const { tournamentId } = useParams<{ tournamentId: string }>();
    const [tournament, setTournament] = useState<Tournament | null>(null);
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
    const [dropdownOpen, setDropdownOpen] = useState(false);

    const loadTournament = useCallback(async () => {
        if (!tournamentId) {
            setError('No tournament ID provided');
            setLoading(false);
            return;
        }

        const [tournamentRes, eventsRes] = await Promise.all([
            getTournamentById(tournamentId),
            getTournamentEvents(tournamentId)
        ]);

        if (tournamentRes.success && tournamentRes.data) {
            setTournament(tournamentRes.data);
            setError('');
        } else {
            setError(tournamentRes.error || 'Tournament not found');
            setLoading(false);
            return;
        }

        if (eventsRes.success && eventsRes.data) {
            setEvents(eventsRes.data);
            // Auto-select first event and fetch full data
            if (eventsRes.data.length > 0) {
                const firstEvent = eventsRes.data[0];
                setSelectedEvent(firstEvent);

                // Immediately fetch full event data (pools, bracket, etc.)
                const fullData = await pollEvent(firstEvent.id);
                if (fullData.success && fullData.data) {
                    setSelectedEvent(fullData.data);
                    setEvents(prev => prev.map(e => e.id === fullData.data!.id ? fullData.data! : e));
                }
            }
            setLastUpdated(new Date());
        }
        setLoading(false);
    }, [tournamentId]);

    useEffect(() => {
        loadTournament();
    }, [loadTournament]);

    // Subscribe to live updates for selected event
    useEffect(() => {
        if (selectedEvent) {
            const unsubscribe = subscribeEvent(selectedEvent.id, (updated) => {
                setSelectedEvent(updated);
                setEvents(prev => prev.map(e => e.id === updated.id ? updated : e));
                setLastUpdated(new Date());
            });
            return unsubscribe;
        }
    }, [selectedEvent?.id]);

    // Poll removed — using WebSocket (subscribeEvent) for real-time updates

    const handleSelectEvent = async (event: Event) => {
        setSelectedEvent(event); // Show immediately with whatever data we have
        setDropdownOpen(false);

        // Immediately fetch full event data (don't wait for next poll)
        const response = await pollEvent(event.id);
        if (response.success && response.data) {
            setSelectedEvent(response.data);
            setEvents(prev => prev.map(e => e.id === response.data!.id ? response.data! : e));
            setLastUpdated(new Date());
        }
    };

    const getStatusLabel = (status: Event['status']) => {
        switch (status) {
            case 'setup': return 'Setting Up';
            case 'pool_play': return 'Pool Play';
            case 'elimination': return 'Playoffs';
            case 'completed': return 'Complete';
            default: return status;
        }
    };

    if (loading) {
        return (
            <div className="event-viewer-page">
                <Header />
                <main className="viewer-content">
                    <div className="loading-state">
                        <RefreshCw className="spin" size={32} />
                        <p>Loading tournament...</p>
                    </div>
                </main>
            </div>
        );
    }

    if (error || !tournament) {
        return (
            <div className="event-viewer-page">
                <Header />
                <main className="viewer-content">
                    <div className="error-state">
                        <h2>Tournament Not Found</h2>
                        <p>{error || 'The tournament you\'re looking for doesn\'t exist or has been deleted.'}</p>
                    </div>
                </main>
            </div>
        );
    }

    return (
        <div className="event-viewer-page">
            <Header />

            <main className="viewer-content">
                <div className="viewer-header">
                    <div className="event-info">
                        <h1>{tournament.name}</h1>

                        {/* Event Selector Dropdown */}
                        {events.length > 0 && (
                            <div className="tournament-selector">
                                <button
                                    className={`selector-btn ${dropdownOpen ? 'open' : ''}`}
                                    onClick={() => setDropdownOpen(!dropdownOpen)}
                                >
                                    <Trophy size={16} />
                                    <span>{selectedEvent?.name || 'Select Event'}</span>
                                    {selectedEvent && (
                                        <span className={`status-badge status-${selectedEvent.status}`}>
                                            {getStatusLabel(selectedEvent.status)}
                                        </span>
                                    )}
                                    <ChevronDown size={16} className={`dropdown-arrow ${dropdownOpen ? 'open' : ''}`} />
                                </button>

                                {dropdownOpen && (
                                    <ul className="tournament-dropdown-menu">
                                        {events.map(e => (
                                            <li key={e.id}>
                                                <button
                                                    className={`dropdown-item ${selectedEvent?.id === e.id ? 'active' : ''}`}
                                                    onClick={() => handleSelectEvent(e)}
                                                >
                                                    <span className="tournament-name">{e.name}</span>
                                                    <span className={`status-badge status-${e.status}`}>
                                                        {getStatusLabel(e.status)}
                                                    </span>
                                                </button>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        )}

                        <div className="meta-badges">
                            {selectedEvent && selectedEvent.status !== 'completed' && (
                                <span className="live-badge">
                                    <Wifi size={12} />
                                    Live
                                </span>
                            )}
                        </div>
                    </div>

                    {lastUpdated && (
                        <div className="last-updated">
                            Last updated: {lastUpdated.toLocaleTimeString()}
                        </div>
                    )}
                </div>

                {!selectedEvent ? (
                    <div className="no-tournaments">
                        <Trophy size={48} />
                        <h2>No Events</h2>
                        <p>This tournament doesn't have any events yet.</p>
                    </div>
                ) : (
                    <TournamentTabs
                        hasPoolPlay={selectedEvent.pools.length > 0}
                        hasPlayoffs={!!selectedEvent.eliminationBracket}
                    >
                        {{
                            poolPlay: (
                                <section className="viewer-section">
                                    {selectedEvent.pools.map(pool => (
                                        <div key={pool.id} className="pool-view-container">
                                            <div className="pool-view-grid">
                                                <PoolStandings
                                                    poolName={pool.name}
                                                    standings={pool.standings}
                                                    highlightTop={2}
                                                />

                                                <div className="pool-matches-view">
                                                    <h4>Match Results</h4>
                                                    <div className="matches-view-grid">
                                                        {pool.matches.map((match) => (
                                                            <MatchCard
                                                                key={match.id}
                                                                match={match}
                                                                teams={selectedEvent.teams}
                                                                isAdmin={false}
                                                                poolTeamIds={pool.teamIds}
                                                            />
                                                        ))}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </section>
                            ),
                            playoffs: selectedEvent.eliminationBracket && (
                                <section className="viewer-section">
                                    <EliminationBracket
                                        bracket={selectedEvent.eliminationBracket}
                                        teams={selectedEvent.teams}
                                        isAdmin={false}
                                        hasThirdPlaceMatch={!!selectedEvent.eliminationBracket.thirdPlaceMatch}
                                    />
                                </section>
                            )
                        }}
                    </TournamentTabs>
                )}
            </main>
        </div>
    );
}
```

- [ ] **Step 2: Copy `EventViewerPage.css` to `TournamentViewerPage.css`**

Read `frontend/src/pages/EventViewerPage.css` and write its exact content, unchanged, to `frontend/src/pages/TournamentViewerPage.css`.

- [ ] **Step 3: Delete old files**

Delete `frontend/src/pages/EventViewerPage.tsx` and `frontend/src/pages/EventViewerPage.css`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/TournamentViewerPage.tsx frontend/src/pages/TournamentViewerPage.css
git rm frontend/src/pages/EventViewerPage.tsx frontend/src/pages/EventViewerPage.css
git commit -m "feat: rename EventViewerPage to TournamentViewerPage, dropdown browses events within a tournament"
```

---

### Task 9: Retype `AdminDashboard.tsx`

The most complex file — no rename (keeps its name), but every piece of state and every handler retypes from the old Tournament-is-the-competition-unit model to the new Event-is-the-competition-unit model.

**Files:**
- Modify: `frontend/src/pages/AdminDashboard.tsx` (full rewrite)
- Not modified: `frontend/src/pages/AdminDashboard.css` (untouched — page doesn't rename)

**Interfaces:**
- Consumes: `getEventById`, `getAllEvents`, `getAllTournaments`, `updateMatchScore`, `subscribeEvent` (Task 1), `Event`, `Tournament` (Task 1), `Header`, `PoolStandings`, `MatchCard`, `EliminationBracket`, `QRCodeShare`, `TournamentTabs` (unchanged components).
- Produces: `AdminDashboard` component — consumed by `App.tsx` (Task 4).

- [ ] **Step 1: Rewrite `AdminDashboard.tsx`**

```tsx
import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Header } from '../components/Header';
import { PoolStandings } from '../components/PoolStandings';
import { MatchCard } from '../components/MatchCard';
import { EliminationBracket } from '../components/EliminationBracket';
import { QRCodeShare } from '../components/QRCodeShare';
import { TournamentTabs } from '../components/TournamentTabs';
import {
    getEventById,
    getAllEvents,
    updateMatchScore,
    subscribeEvent,
    getAllTournaments,
} from '../api';
import { Event, Tournament } from '../api/types';
import { Share2, RefreshCw, ChevronDown, Calendar } from 'lucide-react';
import './AdminDashboard.css';

export function AdminDashboard() {
    const [searchParams] = useSearchParams();
    const eventId = searchParams.get('id');

    const [events, setEvents] = useState<Event[]>([]);
    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [expandedTournaments, setExpandedTournaments] = useState<Set<string>>(new Set());
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
    const [shareTournamentId, setShareTournamentId] = useState<string | null>(null);
    const [showShareModal, setShowShareModal] = useState(false);
    const [loading, setLoading] = useState(true);
    const [hasThirdPlaceMatch, setHasThirdPlaceMatch] = useState(false);

    const loadSidebarData = useCallback(async () => {
        const [eventsRes, tournamentsRes] = await Promise.all([
            getAllEvents(),
            getAllTournaments()
        ]);
        if (eventsRes.success && eventsRes.data) {
            setEvents(eventsRes.data);
        }
        if (tournamentsRes.success && tournamentsRes.data) {
            setTournaments(tournamentsRes.data);
        }
    }, []);

    const loadSelectedEvent = useCallback(async (id: string) => {
        const response = await getEventById(id);
        if (response.success && response.data) {
            setSelectedEvent(response.data);
        }
    }, []);

    useEffect(() => {
        const init = async () => {
            setLoading(true);
            await loadSidebarData();
            if (eventId) {
                await loadSelectedEvent(eventId);
            }
            setLoading(false);
        };
        init();
    }, [eventId, loadSidebarData, loadSelectedEvent]);

    // Subscribe to live updates
    useEffect(() => {
        if (selectedEvent) {
            const unsubscribe = subscribeEvent(selectedEvent.id, (updated) => {
                setSelectedEvent(updated);
            });
            return unsubscribe;
        }
    }, [selectedEvent?.id]);

    const handleScoreUpdate = async (matchId: string, team1Score: number, team2Score: number) => {
        if (!selectedEvent) return;

        const response = await updateMatchScore(selectedEvent.id, {
            matchId,
            team1Score,
            team2Score,
        });

        if (response.success && response.data) {
            setSelectedEvent(response.data);
            // Also update the events list
            setEvents(prev =>
                prev.map(e => e.id === response.data!.id ? response.data! : e)
            );
        }
    };

    const handleSelectEvent = async (id: string) => {
        await loadSelectedEvent(id);
        window.history.replaceState({}, '', `/admin?id=${id}`);
    };

    const getStatusLabel = (status: Event['status']) => {
        switch (status) {
            case 'setup': return 'Setup';
            case 'pool_play': return 'Pool Play';
            case 'elimination': return 'Playoffs';
            case 'completed': return 'Complete';
            default: return status;
        }
    };

    const toggleTournamentExpand = (tournamentId: string) => {
        setExpandedTournaments(prev => {
            const next = new Set(prev);
            if (next.has(tournamentId)) {
                next.delete(tournamentId);
            } else {
                next.add(tournamentId);
            }
            return next;
        });
    };

    const getEventsForTournament = (tournamentId: string) => {
        return events.filter(e => e.tournamentId === tournamentId);
    };

    if (loading) {
        return (
            <div className="admin-page">
                <Header />
                <main className="admin-content">
                    <div className="loading-state">Loading tournaments...</div>
                </main>
            </div>
        );
    }

    return (
        <div className="admin-page">
            <Header />

            <main className="admin-content">
                <aside className="tournaments-sidebar">
                    <div className="sidebar-header">
                        <h2>Tournaments</h2>
                        <Link to="/tournaments" className="manage-events-btn">
                            <Calendar size={16} />
                        </Link>
                    </div>

                    {tournaments.length === 0 ? (
                        <div className="empty-state">
                            <p>No tournaments yet</p>
                            <Link to="/tournaments" className="create-link">Create your first tournament</Link>
                        </div>
                    ) : (
                        <ul className="event-list">
                            {tournaments.map(tournament => (
                                <li key={tournament.id} className="event-item">
                                    <button
                                        className={`event-header-btn ${expandedTournaments.has(tournament.id) ? 'expanded' : ''}`}
                                        onClick={() => toggleTournamentExpand(tournament.id)}
                                    >
                                        <ChevronDown
                                            size={16}
                                            className={`expand-icon ${expandedTournaments.has(tournament.id) ? 'rotated' : ''}`}
                                        />
                                        <span className="event-name">{tournament.name}</span>
                                        <span className="tournament-count">
                                            {getEventsForTournament(tournament.id).length}
                                        </span>
                                    </button>

                                    {expandedTournaments.has(tournament.id) && (
                                        <ul className="tournament-dropdown">
                                            {getEventsForTournament(tournament.id).length === 0 ? (
                                                <li className="no-tournaments">
                                                    <Link to={`/setup?tournamentId=${tournament.id}`}>+ Add event</Link>
                                                </li>
                                            ) : (
                                                getEventsForTournament(tournament.id).map(e => (
                                                    <li
                                                        key={e.id}
                                                        className={`tournament-item ${selectedEvent?.id === e.id ? 'active' : ''}`}
                                                    >
                                                        <button
                                                            className="tournament-select-btn"
                                                            onClick={() => handleSelectEvent(e.id)}
                                                        >
                                                            <span className="tournament-name">{e.name}</span>
                                                            <span className={`tournament-status status-${e.status}`}>
                                                                {getStatusLabel(e.status)}
                                                            </span>
                                                        </button>
                                                    </li>
                                                ))
                                            )}
                                        </ul>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}
                </aside>

                <div className="tournament-detail">
                    {!selectedEvent ? (
                        <div className="no-selection">
                            <h2>Select an event</h2>
                            <p>Choose an event from the sidebar or go to Tournaments to create one</p>
                            <Link to="/tournaments" className="create-btn-large">
                                <Calendar size={20} />
                                Go to Tournaments
                            </Link>
                        </div>
                    ) : (
                        <>
                            <div className="detail-header">
                                <div className="header-info">
                                    <h1>{selectedEvent.name}</h1>
                                    <span className={`status-badge status-${selectedEvent.status}`}>
                                        {getStatusLabel(selectedEvent.status)}
                                    </span>
                                </div>
                                <div className="header-actions">
                                    <button
                                        className="action-btn refresh"
                                        onClick={() => loadSelectedEvent(selectedEvent.id)}
                                    >
                                        <RefreshCw size={16} />
                                    </button>
                                    <button
                                        className="action-btn share"
                                        onClick={() => {
                                            setShareTournamentId(selectedEvent.tournamentId);
                                            setShowShareModal(true);
                                        }}
                                    >
                                        <Share2 size={16} />
                                        Share Tournament
                                    </button>
                                </div>
                            </div>

                            <TournamentTabs
                                hasPoolPlay={selectedEvent.pools.length > 0}
                                hasPlayoffs={!!selectedEvent.eliminationBracket}
                            >
                                {{
                                    poolPlay: (
                                        <section className="pools-section">
                                            {selectedEvent.pools.map(pool => (
                                                <div key={pool.id} className="pool-container">
                                                    <div className="pool-grid">
                                                        <PoolStandings
                                                            poolName={pool.name}
                                                            standings={pool.standings}
                                                            highlightTop={2}
                                                            showQualifyBadge={false}
                                                        />

                                                        <div className="pool-matches">
                                                            <h4>Matches</h4>
                                                            <div className="matches-grid">
                                                                {pool.matches.map((match) => (
                                                                    <MatchCard
                                                                        key={match.id}
                                                                        match={match}
                                                                        teams={selectedEvent.teams}
                                                                        isAdmin={true}
                                                                        poolTeamIds={pool.teamIds}
                                                                        onScoreUpdate={handleScoreUpdate}
                                                                    />
                                                                ))}
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            ))}
                                        </section>
                                    ),
                                    playoffs: (
                                        <section className="elimination-section">
                                            {selectedEvent.eliminationBracket ? (
                                                <EliminationBracket
                                                    bracket={selectedEvent.eliminationBracket}
                                                    teams={selectedEvent.teams}
                                                    isAdmin={true}
                                                    hasThirdPlaceMatch={hasThirdPlaceMatch}
                                                    onThirdPlaceToggle={setHasThirdPlaceMatch}
                                                    onScoreUpdate={handleScoreUpdate}
                                                />
                                            ) : (
                                                <div className="empty-bracket-message">
                                                    <div className="empty-icon">🏆</div>
                                                    <h3>Playoffs Not Started</h3>
                                                    <p>Complete all pool matches to generate the elimination bracket.</p>
                                                </div>
                                            )}
                                        </section>
                                    )
                                }}
                            </TournamentTabs>
                        </>
                    )}
                </div>
            </main>

            {/* Share Modal */}
            {showShareModal && shareTournamentId && (
                <div className="modal-overlay" onClick={() => setShowShareModal(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <button className="modal-close" onClick={() => setShowShareModal(false)}>×</button>
                        <QRCodeShare
                            url={`${window.location.origin}/view/tournament/${shareTournamentId}`}
                            tournamentName={tournaments.find(t => t.id === shareTournamentId)?.name || 'Tournament'}
                        />
                    </div>
                </div>
            )}
        </div>
    );
}
```

Note: the share button is now unconditional (`selectedEvent.tournamentId` is always present per the new required field — the old `{selectedTournament.eventId && (...)}` conditional wrapper is gone). Note the local helper `getEventsForTournament` is deliberately not named `getTournamentEvents` — that name is the imported async API function (Task 1); naming the synchronous local filter differently avoids shadowing/confusion even though this file doesn't import that API function (it already has `events` loaded client-side).

- [ ] **Step 2: Compile check**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: clean (exit 0) — this is the last page-level file, so after this task every file's types should agree with each other.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/AdminDashboard.tsx
git commit -m "feat: retype AdminDashboard to Tournament-sidebar/Event-detail model"
```

---

### Task 10: Backend addendum — `SpaForwardingController.java` and `SecurityConfig.java`

**Files:**
- Modify: `backend/src/main/java/com/example/backend/controller/SpaForwardingController.java`
- Modify: `backend/src/main/java/com/example/backend/config/SecurityConfig.java`

**Interfaces:** none — pure route-string replacement, no code dependencies.

- [ ] **Step 1: Update `SpaForwardingController.java`**

In the `@RequestMapping` value array, replace:
```java
            "/events",
            "/events/**",
```
with:
```java
            "/tournaments",
            "/tournaments/**",
```
(Keep `/`, `/login`, `/signup`, `/admin`, `/admin/**`, `/setup`, `/setup/**`, `/view/**` unchanged — this is a replacement of the two `/events` entries only, not an addition alongside them, since no frontend route serves `/events` as an SPA path after this plan.)

- [ ] **Step 2: Update `SecurityConfig.java`**

In the SPA static-route permit list (the block starting `.requestMatchers("/login", "/signup", "/admin", "/admin/**", "/setup", "/setup/**",`), replace:
```java
                                "/events", "/events/**", "/view/**")
```
with:
```java
                                "/tournaments", "/tournaments/**", "/view/**")
```
Leave the API permit-list matchers (`GET /api/v1/tournaments/{id}`, `GET /api/v1/tournaments/{id}/events`, `GET /api/v1/events/{id}`) untouched — those are unrelated API paths from the backend swap, already correct.

- [ ] **Step 3: Compile check**

Run: `cd backend && export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10" && ./mvnw compile -q`
Expected: clean (exit 0).

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/example/backend/controller/SpaForwardingController.java backend/src/main/java/com/example/backend/config/SecurityConfig.java
git commit -m "fix: update backend SPA route permit lists from /events to /tournaments"
```

---

### Task 11: Full build and manual verification

**Files:** none (verification only).

- [ ] **Step 1: Clean TypeScript build**

Run: `cd frontend && npx tsc -b --noEmit`
Expected: exit 0, no errors.

- [ ] **Step 2: Production build**

Run: `cd frontend && npm run build`
Expected: exit 0, `dist/` produced, no errors.

- [ ] **Step 3: Manual click-through**

Backend must be running against a schema-reset database (per the backend plan's documented requirement) with the frontend dev server pointed at it (`npm run dev`, `VITE_API_URL`/`VITE_WS_URL` from `.env.development`). Walk the golden path:

1. Sign up / log in.
2. Create a tournament (`/tournaments` → "Create Tournament").
3. Open the tournament, add an event with 2 pools of 3 teams each (`/tournaments/:id` → "Add Event" → `EventSetup`).
4. Verify pool-play matches were generated (round-robin) in `AdminDashboard`.
5. Score matches as admin until both pools complete; verify standings update live and the elimination bracket appears.
6. Score bracket matches through to a champion.
7. Open the public viewer link (`/view/tournament/:tournamentId`, from the Share button) in a second browser tab/incognito window — verify it loads without auth, shows the event dropdown, and live-updates via WebSocket when a score changes in the admin tab.
8. Delete an event from `TournamentDetailPage`, then delete the tournament itself; verify both navigate correctly and don't error.

- [ ] **Step 4: If anything fails**

Every file's exact new content is specified in Tasks 1-10 — a failure here means a step was skipped or mistyped, not a design gap. Fix and re-run Steps 1-3.

---

## Self-Review

**Spec coverage:** Naming/route mapping → Tasks 4-9. API layer → Task 1. Page-level flow changes → Tasks 5-9 (including the real-delete-replaces-unlink change in Task 6, the required-tournamentId guard in Task 7, the unconditional share button in Task 9). Small backend addendum → Task 10. Data flow/WS retargeting → Task 1 (`subscribeEvent`) and Tasks 8-9 (consumers). File-level rename pattern → matches the spec's table exactly. Every spec section has a task; no gaps found.

**Placeholder scan:** No "TBD"/"similar to Task N" — every file has full content, including the CSS-copy steps (explicit "read X, write unchanged to Y" instruction rather than a vague "copy the file").

**Type consistency:** `Event`/`Tournament`/`Match`/`Pool`/`EliminationBracket` field names (`tournamentId`, `eventId`) used consistently across Tasks 1-9 — cross-checked `eventLogic.ts`'s `generateEliminationBracket`/`advanceLoserToThirdPlace` against `EliminationBracket.tsx`'s own third-place construction (both use `eventId` now). `getTournamentEvents` (API function, Task 1) vs. `getEventsForTournament` (local synchronous helper, Task 9) deliberately kept distinct to avoid name confusion — noted inline in Task 9. `MatchCard`/`PoolStandings`/`TournamentTabs`/`QRCodeShare`/`ProtectedRoute` confirmed (by direct read) to need zero changes — no task touches them beyond what's listed. `LandingPage`/`LoginPage`/`SignupPage` confirmed (by direct read) to have no structural Tournament/Event dependency — correctly excluded from the file-level task list (the spec's mention of a LandingPage copy-only edit was a design-time overestimate, corrected during planning; no task references it).
