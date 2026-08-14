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

export interface ScoreRules {
  targetScore: number;
  winByTwo: boolean;
  scoreCap: number;
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
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FORFEIT' | 'WALKOVER' | 'SKIPPED';
  targetScore: number;
  winByTwo: boolean;
  scoreCap: number;
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
  consolationBracket?: EliminationBracket | null;
  grandFinalGame1?: Match | null;
  grandFinalGame2?: Match | null;
}

export type EventFormat = 'POOL_TO_ELIM' | 'ROUND_ROBIN_ONLY' | 'POOL_TO_SERIES_AB' | 'POOL_TO_DOUBLE_ELIM';

export interface Event {
  id: string; // uuid
  tournamentId: string; // uuid — required
  name: string;
  status: 'SETUP' | 'POOL_PLAY' | 'ELIMINATION' | 'COMPLETED';
  format: EventFormat;
  advancementPerPool: number;
  wildcardCount: number;
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
  format?: EventFormat;
  advancementPerPool?: number;
  wildcardCount?: number;
  poolStageRules?: ScoreRules;
  playoffStageRules?: ScoreRules;
}

export interface ScoreUpdateRequest {
  matchId: string; // uuid
  team1Score: number;
  team2Score: number;
}

export interface ForfeitRequest {
  winnerId: string; // uuid
  status: 'FORFEIT' | 'WALKOVER';
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
