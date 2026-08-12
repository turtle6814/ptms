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

export async function updateMatchRules(
    eventId: string,
    matchId: string,
    rules: ScoreRules
): Promise<ApiResponse<any>> {
    try {
        await client.patch<ApiResponse<any>>(
            `/events/${eventId}/matches/${matchId}/rules`,
            rules
        );
        return await getEventById(eventId);
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to update scoring rules',
        };
    }
}

export async function recordForfeit(
    eventId: string,
    matchId: string,
    payload: ForfeitRequest
): Promise<ApiResponse<any>> {
    try {
        await client.put<ApiResponse<any>>(
            `/events/${eventId}/matches/${matchId}/forfeit`,
            payload
        );
        return await getEventById(eventId);
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to record forfeit',
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
