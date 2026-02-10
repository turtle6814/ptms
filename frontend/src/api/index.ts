import client from './client';
import {
    ApiResponse,
    LoginRequest,
    SignupRequest,
    AuthResponse,
    User,
    Event,
    CreateEventRequest,
    UpdateEventRequest,
    Tournament,
    CreateTournamentRequest,
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
    // We can just clear the token in the context/storage
    return { success: true };
}

// ================================
// Event API
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

export async function updateEvent(id: string, payload: UpdateEventRequest): Promise<ApiResponse<Event>> {
    try {
        const response = await client.put<ApiResponse<Event>>(`/events/${id}`, payload);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to update event',
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

export async function addTournamentToEvent(eventId: string, tournamentId: string): Promise<ApiResponse<Event>> {
    try {
        const response = await client.post<ApiResponse<Event>>(`/events/${eventId}/tournaments/${tournamentId}`);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to add tournament to event',
        };
    }
}

export async function removeTournamentFromEvent(eventId: string, tournamentId: string): Promise<ApiResponse<Event>> {
    try {
        const response = await client.delete<ApiResponse<Event>>(`/events/${eventId}/tournaments/${tournamentId}`);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to remove tournament from event',
        };
    }
}

export async function getEventTournaments(eventId: string): Promise<ApiResponse<Tournament[]>> {
    try {
        const response = await client.get<ApiResponse<Tournament[]>>(`/events/${eventId}/tournaments`);
        return response.data;
    } catch (error: any) {
        return {
            success: false,
            error: error.response?.data?.error || error.message || 'Failed to get event tournaments',
        };
    }
}

// ================================
// Tournament API
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

export async function getTournament(id: string): Promise<ApiResponse<Tournament>> {
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

// ================================
// Match API
// ================================

export async function updateMatchScore(
    tournamentId: string, // Kept for interface compatibility but might be unused if backend uses matchId directly
    update: ScoreUpdateRequest
): Promise<ApiResponse<any>> { // Returns MatchDTO usually, but frontend might expect Tournament Update?
    // Backend API: PUT /api/v1/tournaments/{tournamentId}/matches/{matchId}/score
    try {
        // Backend returns MatchDTO, but our simulated logic returned the whole Tournament.
        // We might need to refetch the tournament or return the match and have the frontend handle it.
        // For now, let's call the score update, then re-fetch the tournament to be safe and consistent with previous "refresh" behavior.

        await client.put<ApiResponse<any>>(
            `/tournaments/${tournamentId}/matches/${update.matchId}/score`,
            update
        );

        // Re-fetch the tournament to get the full updated state (standings, bracket advancement, etc.)
        return await getTournament(tournamentId);

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
    return `${baseUrl}/view/event/${tournamentId}`; // Assuming viewer route
}


// ================================
// Real-time (WebSockets)
// ================================
import { Client } from '@stomp/stompjs';

export function subscribeTournament(tournamentId: string, callback: (data: Tournament) => void): () => void {
    const client = new Client({
        brokerURL: 'ws://localhost:8080/ws', // Backend WebSocket endpoint
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
        // console.log('Connected: ' + frame);
        client.subscribe(`/topic/tournament/${tournamentId}`, (message) => {
            if (message.body) {
                const tournament: Tournament = JSON.parse(message.body);
                callback(tournament);
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
export async function pollTournament(id: string): Promise<ApiResponse<Tournament>> {
    return getTournament(id);
}
