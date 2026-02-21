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
// Event Schemas
// ----------------------------------------------------------
export interface Event {
  id: string; // uuid
  name: string;
  description?: string | null;
  startDate?: string | null; // date
  endDate?: string | null; // date
  tournamentIds: string[]; // array of uuid
  createdAt: string; // date-time
  updatedAt: string; // date-time
}

export interface CreateEventRequest {
  name: string;
  description?: string | null;
  startDate?: string | null; // date
  endDate?: string | null; // date
}

export interface UpdateEventRequest {
  name?: string;
  description?: string | null;
  startDate?: string | null; // date
  endDate?: string | null; // date
}

// ----------------------------------------------------------
// Tournament Schemas
// ----------------------------------------------------------
export interface Team {
  id: string; // uuid
  name: string;
  createdAt: string; // date-time
}

export interface Match {
  id: string; // uuid
  tournamentId: string; // uuid
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
  tournamentId: string; // uuid
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
  tournamentId: string; // uuid
  rounds: BracketRound[];
  champion?: string | null; // uuid
  thirdPlaceMatch?: Match | null;
  thirdPlaceTeamId?: string | null; // uuid
}

export interface Tournament {
  id: string; // uuid
  eventId?: string | null; // uuid
  name: string;
  status: 'setup' | 'pool_play' | 'elimination' | 'completed';
  teams: Team[];
  pools: Pool[];
  eliminationBracket?: EliminationBracket | null;
  hasThirdPlaceMatch: boolean;
  createdAt: string; // date-time
  updatedAt: string; // date-time
}

export interface PoolConfig {
  name: string;
  teamNames: string[];
}

export interface CreateTournamentRequest {
  name: string;
  eventId?: string | null; // uuid
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
