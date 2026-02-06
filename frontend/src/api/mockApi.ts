// ================================
// Mock API Service Layer
// ================================
// CENTRALIZED: All backend calls go through this file
// Uses localStorage for persistence and simulates async behavior

import {
    Tournament,
    Team,
    Match,
    Pool,
    ApiResponse,
    CreateTournamentPayload,
    ScoreUpdate,
    User,
    LoginPayload,
    SignupPayload,
    AuthResponse,
    Event,
    CreateEventPayload,
    UpdateEventPayload,
} from './types';

import {
    generateId,
    generatePoolMatches,
    calculatePoolStandings,
    isPoolComplete,
    generateEliminationBracket,
    advanceWinnerInBracket,
    getTopTeamsFromPool,
} from '../utils/tournamentLogic';
import { updateBracketWithPoolWinners } from '../utils/bracketUpdateLogic';

// ================================
// Local Storage Keys
// ================================
const STORAGE_KEYS = {
    TOURNAMENTS: 'pickleball_tournaments',
    EVENTS: 'pickleball_events',
    USERS: 'pickleball_users',
    CURRENT_USER: 'pickleball_current_user',
    AUTH_TOKEN: 'pickleball_auth_token',
};

// ================================
// Simulated Network Delay
// ================================
const NETWORK_DELAY = 300; // ms

function delay(ms: number = NETWORK_DELAY): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// ================================
// Data Access Layer
// ================================

function getTournamentsFromStorage(): Tournament[] {
    const data = localStorage.getItem(STORAGE_KEYS.TOURNAMENTS);
    return data ? JSON.parse(data) : [];
}

function saveTournamentsToStorage(tournaments: Tournament[]): void {
    localStorage.setItem(STORAGE_KEYS.TOURNAMENTS, JSON.stringify(tournaments));
}

function getEventsFromStorage(): Event[] {
    const data = localStorage.getItem(STORAGE_KEYS.EVENTS);
    return data ? JSON.parse(data) : [];
}

function saveEventsToStorage(events: Event[]): void {
    localStorage.setItem(STORAGE_KEYS.EVENTS, JSON.stringify(events));
}

// ================================
// Event System for Live Updates
// ================================
type EventCallback = (tournament: Tournament) => void;
const listeners: Map<string, Set<EventCallback>> = new Map();

export function subscribeTournament(tournamentId: string, callback: EventCallback): () => void {
    if (!listeners.has(tournamentId)) {
        listeners.set(tournamentId, new Set());
    }
    listeners.get(tournamentId)!.add(callback);

    // Return unsubscribe function
    return () => {
        listeners.get(tournamentId)?.delete(callback);
    };
}

function notifyListeners(tournament: Tournament): void {
    const callbacks = listeners.get(tournament.id);
    if (callbacks) {
        callbacks.forEach(cb => cb(tournament));
    }
}

// ================================
// Tournament API
// ================================

export async function createTournament(
    payload: CreateTournamentPayload
): Promise<ApiResponse<Tournament>> {
    await delay();

    try {
        const tournamentId = generateId();
        const now = new Date().toISOString();

        // Create all teams and pools
        const allTeams: Team[] = [];
        const pools: Pool[] = [];

        payload.pools.forEach((poolConfig, index) => {
            const poolTeams: Team[] = poolConfig.teamNames.map(name => ({
                id: generateId(),
                name,
                createdAt: now
            }));
            allTeams.push(...poolTeams);

            const poolId = generateId();
            const teamIds = poolTeams.map(t => t.id);
            const poolMatches = generatePoolMatches(tournamentId, teamIds);

            const pool: Pool = {
                id: poolId,
                tournamentId,
                name: poolConfig.name || `Pool ${String.fromCharCode(65 + index)}`, // Pool A, B, etc.
                teamIds,
                matches: poolMatches,
                standings: [],
                isComplete: false
            };
            pool.standings = calculatePoolStandings(pool, poolTeams);
            pools.push(pool);
        });

        const tournament: Tournament = {
            id: tournamentId,
            eventId: payload.eventId, // Link to parent event if provided
            name: payload.name,
            status: 'pool_play',
            teams: allTeams,
            pools: pools,
            eliminationBracket: generateEliminationBracket(tournamentId, pools, allTeams, true),
            createdAt: now,
            updatedAt: now,
        };

        // Save tournament to storage
        const tournaments = getTournamentsFromStorage();
        tournaments.push(tournament);
        saveTournamentsToStorage(tournaments);

        // If linked to an event, add tournament to that event
        if (payload.eventId) {
            const events = getEventsFromStorage();
            const eventIndex = events.findIndex(e => e.id === payload.eventId);
            if (eventIndex !== -1) {
                events[eventIndex].tournamentIds.push(tournamentId);
                events[eventIndex].updatedAt = now;
                saveEventsToStorage(events);
            }
        }

        return { success: true, data: tournament };
    } catch (error) {
        return { success: false, error: String(error) };
    }
}

export async function getTournament(id: string): Promise<ApiResponse<Tournament>> {
    await delay();

    const tournaments = getTournamentsFromStorage();
    const tournament = tournaments.find(t => t.id === id);

    if (!tournament) {
        return { success: false, error: 'Tournament not found' };
    }

    return { success: true, data: tournament };
}

export async function getAllTournaments(): Promise<ApiResponse<Tournament[]>> {
    await delay();

    const tournaments = getTournamentsFromStorage();
    return { success: true, data: tournaments };
}

export async function deleteTournament(id: string): Promise<ApiResponse<void>> {
    await delay();

    const tournaments = getTournamentsFromStorage();
    const index = tournaments.findIndex(t => t.id === id);

    if (index === -1) {
        return { success: false, error: 'Tournament not found' };
    }

    tournaments.splice(index, 1);
    saveTournamentsToStorage(tournaments);

    return { success: true };
}

// ================================
// Event API
// ================================

export async function getAllEvents(): Promise<ApiResponse<Event[]>> {
    await delay();
    const events = getEventsFromStorage();
    return { success: true, data: events };
}

export async function getEventById(id: string): Promise<ApiResponse<Event>> {
    await delay();
    const events = getEventsFromStorage();
    const event = events.find(e => e.id === id);

    if (!event) {
        return { success: false, error: 'Event not found' };
    }

    return { success: true, data: event };
}

export async function createEvent(payload: CreateEventPayload): Promise<ApiResponse<Event>> {
    await delay();

    if (!payload.name.trim()) {
        return { success: false, error: 'Event name is required' };
    }

    const now = new Date().toISOString();
    const newEvent: Event = {
        id: generateId(),
        name: payload.name.trim(),
        description: payload.description,
        startDate: payload.startDate,
        endDate: payload.endDate,
        tournamentIds: [],
        createdAt: now,
        updatedAt: now,
    };

    const events = getEventsFromStorage();
    events.push(newEvent);
    saveEventsToStorage(events);

    return { success: true, data: newEvent };
}

export async function updateEvent(id: string, payload: UpdateEventPayload): Promise<ApiResponse<Event>> {
    await delay();

    const events = getEventsFromStorage();
    const eventIndex = events.findIndex(e => e.id === id);

    if (eventIndex === -1) {
        return { success: false, error: 'Event not found' };
    }

    const updatedEvent: Event = {
        ...events[eventIndex],
        ...(payload.name !== undefined && { name: payload.name.trim() }),
        ...(payload.description !== undefined && { description: payload.description }),
        ...(payload.startDate !== undefined && { startDate: payload.startDate }),
        ...(payload.endDate !== undefined && { endDate: payload.endDate }),
        updatedAt: new Date().toISOString(),
    };

    events[eventIndex] = updatedEvent;
    saveEventsToStorage(events);

    return { success: true, data: updatedEvent };
}

export async function deleteEvent(id: string): Promise<ApiResponse<void>> {
    await delay();

    const events = getEventsFromStorage();
    const eventIndex = events.findIndex(e => e.id === id);

    if (eventIndex === -1) {
        return { success: false, error: 'Event not found' };
    }

    // Also remove the eventId from all linked tournaments
    const tournaments = getTournamentsFromStorage();
    const updatedTournaments = tournaments.map(t =>
        t.eventId === id ? { ...t, eventId: undefined } : t
    );
    saveTournamentsToStorage(updatedTournaments);

    events.splice(eventIndex, 1);
    saveEventsToStorage(events);

    return { success: true };
}

export async function addTournamentToEvent(eventId: string, tournamentId: string): Promise<ApiResponse<Event>> {
    await delay();

    const events = getEventsFromStorage();
    const eventIndex = events.findIndex(e => e.id === eventId);

    if (eventIndex === -1) {
        return { success: false, error: 'Event not found' };
    }

    const tournaments = getTournamentsFromStorage();
    const tournamentIndex = tournaments.findIndex(t => t.id === tournamentId);

    if (tournamentIndex === -1) {
        return { success: false, error: 'Tournament not found' };
    }

    // Add tournament to event
    if (!events[eventIndex].tournamentIds.includes(tournamentId)) {
        events[eventIndex].tournamentIds.push(tournamentId);
        events[eventIndex].updatedAt = new Date().toISOString();
    }

    // Update tournament's eventId
    tournaments[tournamentIndex].eventId = eventId;
    tournaments[tournamentIndex].updatedAt = new Date().toISOString();

    saveEventsToStorage(events);
    saveTournamentsToStorage(tournaments);

    return { success: true, data: events[eventIndex] };
}

export async function removeTournamentFromEvent(eventId: string, tournamentId: string): Promise<ApiResponse<Event>> {
    await delay();

    const events = getEventsFromStorage();
    const eventIndex = events.findIndex(e => e.id === eventId);

    if (eventIndex === -1) {
        return { success: false, error: 'Event not found' };
    }

    // Remove tournament from event
    events[eventIndex].tournamentIds = events[eventIndex].tournamentIds.filter(id => id !== tournamentId);
    events[eventIndex].updatedAt = new Date().toISOString();

    // Remove eventId from tournament
    const tournaments = getTournamentsFromStorage();
    const tournamentIndex = tournaments.findIndex(t => t.id === tournamentId);
    if (tournamentIndex !== -1) {
        tournaments[tournamentIndex].eventId = undefined;
        tournaments[tournamentIndex].updatedAt = new Date().toISOString();
        saveTournamentsToStorage(tournaments);
    }

    saveEventsToStorage(events);

    return { success: true, data: events[eventIndex] };
}

// Helper: Get tournaments for an event
export async function getEventTournaments(eventId: string): Promise<ApiResponse<Tournament[]>> {
    await delay();

    const events = getEventsFromStorage();
    const event = events.find(e => e.id === eventId);

    if (!event) {
        return { success: false, error: 'Event not found' };
    }

    const tournaments = getTournamentsFromStorage();
    const eventTournaments = tournaments.filter(t => event.tournamentIds.includes(t.id));

    return { success: true, data: eventTournaments };
}

// ================================
// Match Score API
// ================================

export async function updateMatchScore(
    tournamentId: string,
    update: ScoreUpdate
): Promise<ApiResponse<Tournament>> {
    await delay();

    try {
        const tournaments = getTournamentsFromStorage();
        const tournamentIndex = tournaments.findIndex(t => t.id === tournamentId);

        if (tournamentIndex === -1) {
            return { success: false, error: 'Tournament not found' };
        }

        const tournament = { ...tournaments[tournamentIndex] };
        tournament.pools = tournament.pools.map(pool => ({ ...pool, matches: [...pool.matches] }));
        if (tournament.eliminationBracket) {
            tournament.eliminationBracket = {
                ...tournament.eliminationBracket,
                rounds: tournament.eliminationBracket.rounds.map(r => ({
                    ...r,
                    matches: [...r.matches],
                })),
            };
        }

        let matchFound = false;

        // Check pool matches
        for (const pool of tournament.pools) {
            const matchIndex = pool.matches.findIndex(m => m.id === update.matchId);
            if (matchIndex !== -1) {
                const match = { ...pool.matches[matchIndex] };
                match.team1Score = update.team1Score;
                match.team2Score = update.team2Score;
                match.status = 'completed';
                match.winnerId = update.team1Score > update.team2Score ? match.team1Id :
                    update.team2Score > update.team1Score ? match.team2Id : null;
                match.updatedAt = new Date().toISOString();
                pool.matches[matchIndex] = match;

                // Recalculate standings
                pool.standings = calculatePoolStandings(pool, tournament.teams);
                pool.isComplete = isPoolComplete(pool);

                matchFound = true;
                break;
            }
        }

        // Check elimination matches
        if (!matchFound && tournament.eliminationBracket) {
            for (const round of tournament.eliminationBracket.rounds) {
                const matchIndex = round.matches.findIndex(m => m.id === update.matchId);
                if (matchIndex !== -1) {
                    const match = { ...round.matches[matchIndex] };
                    match.team1Score = update.team1Score;
                    match.team2Score = update.team2Score;
                    match.status = 'completed';
                    match.winnerId = update.team1Score > update.team2Score ? match.team1Id :
                        update.team2Score > update.team1Score ? match.team2Id : null;
                    match.updatedAt = new Date().toISOString();
                    round.matches[matchIndex] = match;

                    // Advance winner to next round
                    tournament.eliminationBracket = advanceWinnerInBracket(
                        tournament.eliminationBracket,
                        match
                    );

                    matchFound = true;
                    break;
                }
            }
        }

        if (!matchFound) {
            return { success: false, error: 'Match not found' };
        }

        // Check if should advance to elimination or update bracket
        if (tournament.status === 'pool_play') {
            if (tournament.eliminationBracket) {
                tournament.eliminationBracket = updateBracketWithPoolWinners(
                    tournament.eliminationBracket,
                    tournament.pools,
                    tournament.teams
                );
            }

            if (tournament.pools.every(p => p.isComplete)) {
                tournament.status = 'elimination';
            }
        }

        // Check if tournament is complete
        if (tournament.eliminationBracket?.champion) {
            tournament.status = 'completed';
        }

        tournament.updatedAt = new Date().toISOString();
        tournaments[tournamentIndex] = tournament;
        saveTournamentsToStorage(tournaments);

        // Notify listeners for live updates
        notifyListeners(tournament);

        return { success: true, data: tournament };
    } catch (error) {
        return { success: false, error: String(error) };
    }
}

// ================================
// Live Polling (Simulated WebSocket)
// ================================

export async function pollTournament(id: string): Promise<ApiResponse<Tournament>> {
    // This simulates polling for updates
    // In a real app, this would be a WebSocket connection
    return getTournament(id);
}

// ================================
// Utility: Generate Shareable Link
// ================================

export function generateShareableLink(tournamentId: string): string {
    const baseUrl = window.location.origin;
    return `${baseUrl}/view/${tournamentId}`;
}

// ================================
// Auth Data Access Layer
// ================================

interface StoredUser extends User {
    passwordHash: string; // Simple mock hash
}

function getUsersFromStorage(): StoredUser[] {
    const data = localStorage.getItem(STORAGE_KEYS.USERS);
    return data ? JSON.parse(data) : [];
}

function saveUsersToStorage(users: StoredUser[]): void {
    localStorage.setItem(STORAGE_KEYS.USERS, JSON.stringify(users));
}

function getCurrentUserFromStorage(): User | null {
    const data = localStorage.getItem(STORAGE_KEYS.CURRENT_USER);
    return data ? JSON.parse(data) : null;
}

function saveCurrentUserToStorage(user: User | null): void {
    if (user) {
        localStorage.setItem(STORAGE_KEYS.CURRENT_USER, JSON.stringify(user));
    } else {
        localStorage.removeItem(STORAGE_KEYS.CURRENT_USER);
    }
}

function getAuthTokenFromStorage(): string | null {
    return localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);
}

function saveAuthTokenToStorage(token: string | null): void {
    if (token) {
        localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, token);
    } else {
        localStorage.removeItem(STORAGE_KEYS.AUTH_TOKEN);
    }
}

// Simple mock hash function (NOT for production use!)
function mockHashPassword(password: string): string {
    return btoa(password + '_hashed');
}

function mockVerifyPassword(password: string, hash: string): boolean {
    return mockHashPassword(password) === hash;
}

// Generate a mock JWT token
function generateMockToken(userId: string): string {
    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({
        userId,
        exp: Date.now() + 24 * 60 * 60 * 1000 // 24 hours
    }));
    const signature = btoa('mock_signature');
    return `${header}.${payload}.${signature}`;
}

// ================================
// Auth API
// ================================

export async function signup(
    payload: SignupPayload
): Promise<ApiResponse<AuthResponse>> {
    await delay();

    try {
        const users = getUsersFromStorage();

        // Check if username already exists
        if (users.some(u => u.username.toLowerCase() === payload.username.toLowerCase())) {
            return { success: false, error: 'Username already exists' };
        }

        // Check if phone number already exists
        if (users.some(u => u.phoneNumber === payload.phoneNumber)) {
            return { success: false, error: 'Phone number already registered' };
        }

        const now = new Date().toISOString();
        const newUser: StoredUser = {
            id: generateId(),
            username: payload.username,
            phoneNumber: payload.phoneNumber,
            passwordHash: mockHashPassword(payload.password),
            createdAt: now,
        };

        users.push(newUser);
        saveUsersToStorage(users);

        // Create user object without password
        const user: User = {
            id: newUser.id,
            username: newUser.username,
            phoneNumber: newUser.phoneNumber,
            createdAt: newUser.createdAt,
        };

        const token = generateMockToken(user.id);
        saveCurrentUserToStorage(user);
        saveAuthTokenToStorage(token);

        return { success: true, data: { user, token } };
    } catch (error) {
        return { success: false, error: String(error) };
    }
}

export async function login(
    payload: LoginPayload
): Promise<ApiResponse<AuthResponse>> {
    await delay();

    try {
        const users = getUsersFromStorage();
        const storedUser = users.find(
            u => u.phoneNumber === payload.phoneNumber
        );

        if (!storedUser) {
            return { success: false, error: 'Invalid phone number or password' };
        }

        if (!mockVerifyPassword(payload.password, storedUser.passwordHash)) {
            return { success: false, error: 'Invalid phone number or password' };
        }

        // Create user object without password
        const user: User = {
            id: storedUser.id,
            username: storedUser.username,
            phoneNumber: storedUser.phoneNumber,
            createdAt: storedUser.createdAt,
        };

        const token = generateMockToken(user.id);
        saveCurrentUserToStorage(user);
        saveAuthTokenToStorage(token);

        return { success: true, data: { user, token } };
    } catch (error) {
        return { success: false, error: String(error) };
    }
}

export async function logout(): Promise<ApiResponse<void>> {
    await delay(100);

    saveCurrentUserToStorage(null);
    saveAuthTokenToStorage(null);

    return { success: true };
}

export async function getCurrentUser(): Promise<ApiResponse<User | null>> {
    await delay(100);

    const user = getCurrentUserFromStorage();
    const token = getAuthTokenFromStorage();

    // Verify token is still valid (mock check)
    if (user && token) {
        try {
            const payloadPart = token.split('.')[1];
            const payload = JSON.parse(atob(payloadPart));
            if (payload.exp < Date.now()) {
                // Token expired
                saveCurrentUserToStorage(null);
                saveAuthTokenToStorage(null);
                return { success: true, data: null };
            }
        } catch {
            // Invalid token format
            saveCurrentUserToStorage(null);
            saveAuthTokenToStorage(null);
            return { success: true, data: null };
        }
    }

    return { success: true, data: user };
}

export function isAuthenticated(): boolean {
    return getCurrentUserFromStorage() !== null && getAuthTokenFromStorage() !== null;
}
