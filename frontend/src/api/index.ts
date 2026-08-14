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
    ScoreRules,
    ForfeitRequest,
} from './types';

// ================================
// Shared error normalization
// ================================

interface ErrorWithMessage {
    message?: string;
    response?: { data?: { error?: string } };
}

function apiErrorMessage(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null) {
        const e = error as ErrorWithMessage;
        if (e.response?.data?.error) return e.response.data.error;
        if (e.message) return e.message;
    }
    return fallback;
}

async function apiCall<T>(
    fn: () => Promise<ApiResponse<T>>,
    fallbackMessage: string
): Promise<ApiResponse<T>> {
    try {
        return await fn();
    } catch (error) {
        return {
            success: false,
            error: apiErrorMessage(error, fallbackMessage),
        };
    }
}

// ================================
// Auth API
// ================================

export async function login(payload: LoginRequest): Promise<ApiResponse<AuthResponse>> {
    return apiCall(
        async () => (await client.post<ApiResponse<AuthResponse>>('/auth/login', payload)).data,
        'Login failed'
    );
}

export async function signup(payload: SignupRequest): Promise<ApiResponse<AuthResponse>> {
    return apiCall(
        async () => (await client.post<ApiResponse<AuthResponse>>('/auth/signup', payload)).data,
        'Signup failed'
    );
}

export async function getCurrentUser(): Promise<ApiResponse<User>> {
    return apiCall(
        async () => (await client.get<ApiResponse<User>>('/auth/me')).data,
        'Failed to get current user'
    );
}

export async function logout(): Promise<ApiResponse<void>> {
    // Client-side logout only since JWT is stateless (unless we had a blacklist)
    return { success: true, data: undefined };
}

// ================================
// Tournament API (top-level container)
// ================================

export async function getAllTournaments(): Promise<ApiResponse<Tournament[]>> {
    return apiCall(
        async () => (await client.get<ApiResponse<Tournament[]>>('/tournaments')).data,
        'Failed to fetch tournaments'
    );
}

export async function getTournamentById(id: string): Promise<ApiResponse<Tournament>> {
    return apiCall(
        async () => (await client.get<ApiResponse<Tournament>>(`/tournaments/${id}`)).data,
        'Tournament not found'
    );
}

export async function createTournament(payload: CreateTournamentRequest): Promise<ApiResponse<Tournament>> {
    return apiCall(
        async () => (await client.post<ApiResponse<Tournament>>('/tournaments', payload)).data,
        'Failed to create tournament'
    );
}

export async function updateTournament(id: string, payload: UpdateTournamentRequest): Promise<ApiResponse<Tournament>> {
    return apiCall(
        async () => (await client.put<ApiResponse<Tournament>>(`/tournaments/${id}`, payload)).data,
        'Failed to update tournament'
    );
}

export async function deleteTournament(id: string): Promise<ApiResponse<void>> {
    return apiCall(
        async () => (await client.delete<ApiResponse<void>>(`/tournaments/${id}`)).data,
        'Failed to delete tournament'
    );
}

export async function getTournamentEvents(tournamentId: string): Promise<ApiResponse<Event[]>> {
    return apiCall(
        async () => (await client.get<ApiResponse<Event[]>>(`/tournaments/${tournamentId}/events`)).data,
        'Failed to get tournament events'
    );
}

// ================================
// Event API (competition unit)
// ================================

export async function getAllEvents(): Promise<ApiResponse<Event[]>> {
    return apiCall(
        async () => (await client.get<ApiResponse<Event[]>>('/events')).data,
        'Failed to fetch events'
    );
}

export async function getEventById(id: string): Promise<ApiResponse<Event>> {
    return apiCall(
        async () => (await client.get<ApiResponse<Event>>(`/events/${id}`)).data,
        'Event not found'
    );
}

export async function createEvent(payload: CreateEventRequest): Promise<ApiResponse<Event>> {
    return apiCall(
        async () => (await client.post<ApiResponse<Event>>('/events', payload)).data,
        'Failed to create event'
    );
}

export async function deleteEvent(id: string): Promise<ApiResponse<void>> {
    return apiCall(
        async () => (await client.delete<ApiResponse<void>>(`/events/${id}`)).data,
        'Failed to delete event'
    );
}

// ================================
// Match API
// ================================

export async function updateMatchScore(
    eventId: string,
    update: ScoreUpdateRequest
): Promise<ApiResponse<Event>> {
    // Backend API: PUT /api/v1/events/{eventId}/matches/{matchId}/score
    return apiCall(async () => {
        await client.put(`/events/${eventId}/matches/${update.matchId}/score`, update);
        // Re-fetch the event to get the full updated state (standings, bracket advancement, etc.)
        return getEventById(eventId);
    }, 'Failed to update score');
}

export async function updateMatchRules(
    eventId: string,
    matchId: string,
    rules: ScoreRules
): Promise<ApiResponse<Event>> {
    return apiCall(async () => {
        await client.patch(`/events/${eventId}/matches/${matchId}/rules`, rules);
        return getEventById(eventId);
    }, 'Failed to update scoring rules');
}

export async function recordForfeit(
    eventId: string,
    matchId: string,
    payload: ForfeitRequest
): Promise<ApiResponse<Event>> {
    return apiCall(async () => {
        await client.put(`/events/${eventId}/matches/${matchId}/forfeit`, payload);
        return getEventById(eventId);
    }, 'Failed to record forfeit');
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

// One-shot full refresh (pools, standings, bracket) — used alongside the WebSocket stream
export async function pollEvent(id: string): Promise<ApiResponse<Event>> {
    return getEventById(id);
}
