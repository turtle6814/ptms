// ================================
// Tournament Manager - Type Definitions
// ================================

export interface Team {
  id: string;
  name: string;
  createdAt: string;
}

export interface Match {
  id: string;
  tournamentId: string;
  poolId?: string;          // Present for pool matches
  bracketRound?: number;    // Present for elimination matches
  bracketPosition?: number; // Position in the bracket round
  team1Id: string;
  team2Id: string;
  team1Score: number | null;
  team2Score: number | null;
  winnerId: string | null;
  status: 'pending' | 'in_progress' | 'completed';
  createdAt: string;
  updatedAt: string;
}

export interface PoolStanding {
  teamId: string;
  teamName: string;
  wins: number;
  losses: number;
  pointsFor: number;
  pointsAgainst: number;
  pointDifferential: number;
}

export interface Pool {
  id: string;
  tournamentId: string;
  name: string;
  teamIds: string[];
  matches: Match[];
  standings: PoolStanding[];
  isComplete: boolean;
}

export interface EliminationBracket {
  tournamentId: string;
  rounds: BracketRound[];
  champion: string | null;
}

export interface BracketRound {
  roundNumber: number;
  name: string;  // "Semifinals", "Finals", etc.
  matches: Match[];
}

// ================================
// Event (contains multiple tournaments)
// ================================
export interface Event {
  id: string;
  name: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  tournamentIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Tournament {
  id: string;
  eventId?: string;  // Optional link to parent event
  name: string;
  status: 'setup' | 'pool_play' | 'elimination' | 'completed';
  teams: Team[];
  pools: Pool[];
  eliminationBracket: EliminationBracket | null;
  createdAt: string;
  updatedAt: string;
}

// API Response types
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}

// Score update payload
export interface ScoreUpdate {
  matchId: string;
  team1Score: number;
  team2Score: number;
}

// Create tournament payload
export interface CreateTournamentPayload {
  name: string;
  eventId?: string;  // Optional: link to parent event
  pools: {
    name: string;
    teamNames: string[];
  }[];
}

// Create event payload
export interface CreateEventPayload {
  name: string;
  description?: string;
  startDate?: string;
  endDate?: string;
}

// Update event payload
export interface UpdateEventPayload {
  name?: string;
  description?: string;
  startDate?: string;
  endDate?: string;
}

// ================================
// Auth Types
// ================================

export interface User {
  id: string;
  username: string;
  phoneNumber: string;
  createdAt: string;
}

export interface LoginPayload {
  phoneNumber: string;
  password: string;
}

export interface SignupPayload {
  username: string;
  phoneNumber: string;
  password: string;
}

export interface AuthResponse {
  user: User;
  token: string; // Mocked JWT token
}
