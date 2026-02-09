import React from 'react';
import { EliminationBracket as BracketType, Team } from '../api/types';
import { MatchCard } from './MatchCard';
import { Trophy, Medal } from 'lucide-react';
import './EliminationBracket.css';

interface EliminationBracketProps {
    bracket: BracketType;
    teams: Team[];
    isAdmin?: boolean;
    showPlaceholders?: boolean;
    hasThirdPlaceMatch?: boolean;
    onThirdPlaceToggle?: (enabled: boolean) => void;
    onScoreUpdate?: (matchId: string, team1Score: number, team2Score: number) => void;
}

export function EliminationBracket({
    bracket,
    teams,
    isAdmin = false,
    showPlaceholders = false,
    hasThirdPlaceMatch = false,
    onThirdPlaceToggle,
    onScoreUpdate,
}: EliminationBracketProps) {
    const champion = bracket.champion ? teams.find(t => t.id === bracket.champion) : null;

    // Dynamically compute third-place match from semifinal losers
    const thirdPlaceData = React.useMemo(() => {
        if (!hasThirdPlaceMatch) return { match: null, autoThirdTeam: null };

        // Find semifinals round (round before finals)
        const finalsIndex = bracket.rounds.findIndex(r => r.name === 'Finals');
        if (finalsIndex <= 0) return { match: null, autoThirdTeam: null };

        const semisRound = bracket.rounds[finalsIndex - 1];
        const completedSemis = semisRound.matches.filter(m => m.status === 'completed' && m.winnerId);

        // Get losers from completed semifinals
        const losers = completedSemis.map(m =>
            m.team1Id === m.winnerId ? m.team2Id : m.team1Id
        ).filter(Boolean);

        // If only 1 semifinal exists, auto-3rd place
        if (semisRound.matches.length === 1 && losers.length === 1) {
            return { match: null, autoThirdTeam: teams.find(t => t.id === losers[0]) || null };
        }

        // If we have losers, create a virtual third-place match OR use existing one
        if (losers.length > 0) {
            const existingMatch = bracket.thirdPlaceMatch;

            // Always create/update match with current loser IDs
            const match = {
                id: existingMatch?.id || 'third-place-match',
                tournamentId: bracket.tournamentId,
                bracketRound: finalsIndex + 1,
                bracketPosition: 0,
                team1Id: losers[0] || existingMatch?.team1Id || '',
                team2Id: losers[1] || existingMatch?.team2Id || '',
                team1Score: existingMatch?.team1Score ?? null,
                team2Score: existingMatch?.team2Score ?? null,
                winnerId: existingMatch?.winnerId || null,
                status: existingMatch?.status || 'pending' as const,
                createdAt: existingMatch?.createdAt || new Date().toISOString(),
                updatedAt: new Date().toISOString(),
            };

            return { match, autoThirdTeam: null };
        }

        return { match: null, autoThirdTeam: null };
    }, [hasThirdPlaceMatch, bracket, teams]);

    const thirdPlaceWinner = thirdPlaceData.match?.winnerId
        ? teams.find(t => t.id === thirdPlaceData.match?.winnerId)
        : null;

    return (
        <div className="elimination-bracket">
            <div className="bracket-header">
                <h2 className="bracket-title">
                    <Trophy size={24} />
                    Elimination Bracket
                </h2>

                {isAdmin && onThirdPlaceToggle && (
                    <label className="third-place-toggle">
                        <input
                            type="checkbox"
                            checked={hasThirdPlaceMatch}
                            onChange={(e) => onThirdPlaceToggle(e.target.checked)}
                        />
                        <span>Third-place match?</span>
                    </label>
                )}
            </div>

            {champion && (
                <div className="champion-banner">
                    <div className="champion-trophy">🏆</div>
                    <div className="champion-text">
                        <span className="champion-label">Champion</span>
                        <span className="champion-name">{champion.name}</span>
                    </div>
                </div>
            )}

            {/* Show 2nd place banner - the finals loser */}
            {(() => {
                if (!bracket.champion) return null;
                const finalsRound = bracket.rounds.find(r => r.name === 'Finals');
                const finalsMatch = finalsRound?.matches[0];
                if (!finalsMatch || finalsMatch.status !== 'completed') return null;
                const runnerUpId = finalsMatch.team1Id === bracket.champion
                    ? finalsMatch.team2Id
                    : finalsMatch.team1Id;
                const runnerUp = teams.find(t => t.id === runnerUpId);
                if (!runnerUp) return null;
                return (
                    <div className="runner-up-banner">
                        <div className="runner-up-medal">🥈</div>
                        <div className="runner-up-text">
                            <span className="runner-up-label">2nd Place</span>
                            <span className="runner-up-name">{runnerUp.name}</span>
                        </div>
                    </div>
                );
            })()}

            {/* Show 3rd place banner if applicable */}
            {hasThirdPlaceMatch && (thirdPlaceWinner || thirdPlaceData.autoThirdTeam) && (
                <div className="third-place-banner">
                    <div className="third-place-medal">🥉</div>
                    <div className="third-place-text">
                        <span className="third-place-label">3rd Place</span>
                        <span className="third-place-name">
                            {thirdPlaceWinner?.name || thirdPlaceData.autoThirdTeam?.name}
                        </span>
                    </div>
                </div>
            )}

            <div className="bracket-container">
                {bracket.rounds.map((round, roundIndex) => (
                    <div
                        key={round.roundNumber}
                        className={`bracket-round round-${roundIndex + 1}`}
                    >
                        <h3 className="round-title">{round.name}</h3>
                        <div className="round-matches">
                            {round.matches.map((match) => (
                                <div key={match.id} className="bracket-match-wrapper">
                                    <MatchCard
                                        match={showPlaceholders ? { ...match, team1Id: '', team2Id: '', team1Score: null, team2Score: null, winnerId: null, status: 'pending' } : match}
                                        teams={teams}
                                        isAdmin={isAdmin && !showPlaceholders}
                                        onScoreUpdate={onScoreUpdate}
                                    />
                                </div>
                            ))}
                        </div>

                        {/* Show third-place match below finals */}
                        {round.name === 'Finals' && hasThirdPlaceMatch && thirdPlaceData.match && (
                            <div className="third-place-section">
                                <h4 className="third-place-title">
                                    <Medal size={18} />
                                    Third-Place Match
                                </h4>
                                <div className="bracket-match-wrapper">
                                    <MatchCard
                                        match={thirdPlaceData.match}
                                        teams={teams}
                                        isAdmin={isAdmin}
                                        onScoreUpdate={onScoreUpdate}
                                    />
                                </div>
                            </div>
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
}
