// ================================
// Tournament Logic Utilities
// ================================
// Handles round-robin scheduling, score calculation, and bracket progression

import { Match, Pool, PoolStanding, Team, Tournament, EliminationBracket, BracketRound } from '../api/types';

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
    tournamentId: string,
    // poolId: string,
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
                    tournamentId,
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
        if (match.status === 'completed' && match.team1Score !== null && match.team2Score !== null) {
            const team1Standing = standings.get(match.team1Id)!;
            const team2Standing = standings.get(match.team2Id)!;

            // Update points
            team1Standing.pointsFor += match.team1Score;
            team1Standing.pointsAgainst += match.team2Score;
            team2Standing.pointsFor += match.team2Score;
            team2Standing.pointsAgainst += match.team1Score;

            // Update wins/losses
            if (match.team1Score > match.team2Score) {
                team1Standing.wins++;
                team2Standing.losses++;
            } else if (match.team2Score > match.team1Score) {
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
    tournamentId: string,
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
            tournamentId,
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
                tournamentId,
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
                tournamentId,
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
        tournamentId,
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
        // This happens when the current round has an odd number of matches
        // and this is the last match in the next round
        const currentRoundMatchCount = currentRound.matches.length;
        const isLastMatchInNextRound = nextMatchIndex === nextRound.matches.length - 1;
        const currentRoundIsOdd = currentRoundMatchCount % 2 === 1;

        // If current round has odd matches and this is the last match in next round,
        // only one team will ever fill this match (the winner of the last match in current round)
        // So we should auto-advance this team
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
            tournamentId: bracket.tournamentId,
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
// Tournament State Transitions
// ================================

/**
 * Determine if tournament should advance from pool play to elimination
 */
export function shouldAdvanceToElimination(tournament: Tournament): boolean {
    if (tournament.status !== 'pool_play') return false;
    return tournament.pools.every(pool => isPoolComplete(pool));
}

/**
 * Determine if tournament is complete
 */
export function isTournamentComplete(tournament: Tournament): boolean {
    if (!tournament.eliminationBracket) return false;
    return tournament.eliminationBracket.champion !== null;
}
