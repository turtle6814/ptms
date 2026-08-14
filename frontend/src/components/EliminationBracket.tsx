import React from 'react';
import { EliminationBracket as BracketType, Match, Team } from '../api/types';
import { MatchCard } from './MatchCard';
import { Trophy, Medal } from 'lucide-react';
import './EliminationBracket.css';

interface EliminationBracketProps {
    bracket: BracketType;
    teams: Team[];
    title?: string;
    isAdmin?: boolean;
    showPlaceholders?: boolean;
    hasThirdPlaceMatch?: boolean;
    onThirdPlaceToggle?: (enabled: boolean) => void;
    onScoreUpdate?: (matchId: string, team1Score: number, team2Score: number) => void;
    onForfeit?: (matchId: string, winnerId: string, status: 'FORFEIT' | 'WALKOVER') => void;
}

function getDecidingMatch(bracket: BracketType): Match | null {
    if (bracket.grandFinalGame1) {
        return bracket.grandFinalGame2?.status === 'COMPLETED'
            ? bracket.grandFinalGame2
            : bracket.grandFinalGame1;
    }
    const finalsRound = bracket.rounds.find(r => r.name === 'Finals');
    return finalsRound?.matches[0] ?? null;
}

function buildPlaceholderMatch(match: Match): Match {
    return {
        ...match,
        team1Id: '',
        team2Id: '',
        team1Score: null,
        team2Score: null,
        winnerId: null,
        status: 'PENDING',
    };
}

export function EliminationBracket({
    bracket,
    teams,
    title = 'Elimination Bracket',
    isAdmin = false,
    showPlaceholders = false,
    hasThirdPlaceMatch = false,
    onThirdPlaceToggle,
    onScoreUpdate,
    onForfeit,
}: EliminationBracketProps) {
    const champion = bracket.champion ? teams.find(t => t.id === bracket.champion) : null;

    // Dynamically compute third-place match from semifinal losers
    const thirdPlaceData = React.useMemo(() => {
        if (!hasThirdPlaceMatch) return { match: null, autoThirdTeam: null };

        // Find semifinals round (round before finals)
        const finalsIndex = bracket.rounds.findIndex(r => r.name === 'Finals');
        if (finalsIndex <= 0) return { match: null, autoThirdTeam: null };

        const semisRound = bracket.rounds[finalsIndex - 1];
        const completedSemis = semisRound.matches.filter(m => m.status === 'COMPLETED' && m.winnerId);

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
                eventId: bracket.eventId,
                bracketRound: finalsIndex + 1,
                bracketPosition: 0,
                team1Id: losers[0] || existingMatch?.team1Id || '',
                team2Id: losers[1] || existingMatch?.team2Id || '',
                team1Score: existingMatch?.team1Score ?? null,
                team2Score: existingMatch?.team2Score ?? null,
                winnerId: existingMatch?.winnerId || null,
                status: existingMatch?.status || 'PENDING' as const,
                targetScore: existingMatch?.targetScore ?? 15,
                winByTwo: existingMatch?.winByTwo ?? true,
                scoreCap: existingMatch?.scoreCap ?? 21,
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

    const runnerUp = React.useMemo(() => {
        if (!bracket.champion) return null;
        const decidingMatch = getDecidingMatch(bracket);
        if (!decidingMatch || decidingMatch.status !== 'COMPLETED') return null;
        const runnerUpId = decidingMatch.team1Id === bracket.champion
            ? decidingMatch.team2Id
            : decidingMatch.team1Id;
        return teams.find(t => t.id === runnerUpId) || null;
    }, [bracket, teams]);

    return (
        <div className="elimination-bracket">
            <div className="bracket-header">
                <h2 className="bracket-title">
                    <Trophy size={24} />
                    {title}
                </h2>

                {isAdmin && onThirdPlaceToggle && !bracket.grandFinalGame1 && (
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
            {runnerUp && (
                <div className="runner-up-banner">
                    <div className="runner-up-medal">🥈</div>
                    <div className="runner-up-text">
                        <span className="runner-up-label">2nd Place</span>
                        <span className="runner-up-name">{runnerUp.name}</span>
                    </div>
                </div>
            )}

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
                                        match={showPlaceholders ? buildPlaceholderMatch(match) : match}
                                        teams={teams}
                                        isAdmin={isAdmin && !showPlaceholders}
                                        onScoreUpdate={onScoreUpdate}
                                        onForfeit={onForfeit}
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
                                        onForfeit={onForfeit}
                                    />
                                </div>
                            </div>
                        )}
                    </div>
                ))}
            </div>

            {bracket.grandFinalGame1 && (
                <div className="grand-final-section">
                    <h3 className="round-title">Grand Final</h3>
                    <div className="bracket-match-wrapper">
                        <MatchCard
                            match={bracket.grandFinalGame1}
                            teams={teams}
                            isAdmin={isAdmin}
                            onScoreUpdate={onScoreUpdate}
                            onForfeit={onForfeit}
                        />
                    </div>
                    {bracket.grandFinalGame2 && (
                        bracket.grandFinalGame2.status === 'SKIPPED' ? (
                            <div className="grand-final-note">
                                Not needed — {champion?.name ?? 'the winners’ bracket champion'} won Game 1.
                            </div>
                        ) : (
                            <div className="bracket-match-wrapper">
                                <MatchCard
                                    match={bracket.grandFinalGame2}
                                    teams={teams}
                                    isAdmin={isAdmin}
                                    onScoreUpdate={onScoreUpdate}
                                    onForfeit={onForfeit}
                                />
                            </div>
                        )
                    )}
                </div>
            )}

            {bracket.consolationBracket && (
                // hasThirdPlaceMatch/onThirdPlaceToggle intentionally omitted: the consolation bracket has no third-place match
                <EliminationBracket
                    bracket={bracket.consolationBracket}
                    teams={teams}
                    title="Consolation Bracket (B Bracket)"
                    isAdmin={isAdmin}
                    showPlaceholders={showPlaceholders}
                    onScoreUpdate={onScoreUpdate}
                    onForfeit={onForfeit}
                />
            )}
        </div>
    );
}
