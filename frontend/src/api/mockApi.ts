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

// ================================
// Local Storage Keys
// ================================
const STORAGE_KEYS = {
    TOURNAMENTS: 'pickleball_tournaments',
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
            const poolMatches = generatePoolMatches(tournamentId, poolId, teamIds);

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
            name: payload.name,
            status: 'pool_play',
            teams: allTeams,
            pools: pools,
            eliminationBracket: null,
            createdAt: now,
            updatedAt: now,
        };

        // Save to storage
        const tournaments = getTournamentsFromStorage();
        tournaments.push(tournament);
        saveTournamentsToStorage(tournaments);

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

        // Check if should advance to elimination
        if (tournament.status === 'pool_play' && tournament.pools.every(p => p.isComplete)) {
            // Generate elimination bracket using all pools
            tournament.eliminationBracket = generateEliminationBracket(
                tournamentId,
                tournament.pools,
                tournament.teams
            );
            tournament.status = 'elimination';
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
