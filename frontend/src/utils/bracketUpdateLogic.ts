
import { EliminationBracket, Pool, Team } from '../api/types';
import { isPoolComplete, getTopTeamsFromPool } from './tournamentLogic';

/**
 * Update the existing elimination bracket with winners from eligible pools.
 * Does not replace the bracket, but modifies specific matches in-place.
 */
export function updateBracketWithPoolWinners(
    bracket: EliminationBracket,
    pools: Pool[],
    teams: Team[]
): EliminationBracket {
    if (!bracket.rounds || bracket.rounds.length === 0) return bracket;

    const firstRound = bracket.rounds[0];
    const poolCount = pools.length;
    const matchesMap = new Map(firstRound.matches.map(m => [m.bracketPosition, m]));
    let hasUpdates = false;

    // Helper to update a match if it exists
    const updateMatch = (position: number, teamId: string, slot: 'team1' | 'team2') => {
        const match = matchesMap.get(position);
        if (match) {
            if (slot === 'team1' && match.team1Id !== teamId) {
                match.team1Id = teamId;
                hasUpdates = true;
            } else if (slot === 'team2' && match.team2Id !== teamId) {
                match.team2Id = teamId;
                hasUpdates = true;
            }
        }
    };

    if (poolCount === 1) {
        // Single Pool: Top 2 go to Finals (Position 1)
        const pool = pools[0];
        if (isPoolComplete(pool)) {
            const topTeams = getTopTeamsFromPool(pool, teams, 2);
            // Rank 1 -> Team 1, Rank 2 -> Team 2
            if (topTeams[0]) updateMatch(1, topTeams[0], 'team1');
            if (topTeams[1]) updateMatch(1, topTeams[1], 'team2');
        }
    } else {
        // Multiple Pools: Cross-pool pairing
        pools.forEach((pool, i) => {
            if (isPoolComplete(pool)) {
                const topTeams = getTopTeamsFromPool(pool, teams, 2);
                const rank1Id = topTeams[0];
                const rank2Id = topTeams[1];

                // Logic matches generateEliminationBracket:
                // Match i (Position i+1): Team 1 comes from Pool i Rank 1
                if (rank1Id) updateMatch(i + 1, rank1Id, 'team1');

                // Match (prevPool) (Position ...): Team 2 comes from Pool i Rank 2
                // Pair: Pool(prev) #1 vs Pool(i) #2
                // So Pool i Rank 2 goes to the Match that took Pool(prev) #1
                // Which was Match (i-1).
                // Wait, logic in generator:
                // pool1Index = i, pool2Index = (i+1)%N
                // Match i takes Pool i #1 AND Pool (i+1) #2

                // So Pool i #2 goes to Match where it is the SECOND pool.
                // That is Match where pool2Index == i.
                // (x + 1) % N = i  =>  x = (i - 1 + N) % N
                const targetMatchIndex = (i - 1 + poolCount) % poolCount;
                if (rank2Id) updateMatch(targetMatchIndex + 1, rank2Id, 'team2');
            }
        });
    }

    if (!hasUpdates) return bracket;

    //Return new object references to trigger React updates
    return {
        ...bracket,
        rounds: bracket.rounds.map((r, i) => i === 0 ? { ...r, matches: Array.from(matchesMap.values()) } : r)
    };
}
