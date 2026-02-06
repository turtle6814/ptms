import { EliminationBracket as BracketType, Team } from '../api/types';
import { MatchCard } from './MatchCard';
import { Trophy } from 'lucide-react';
import './EliminationBracket.css';

interface EliminationBracketProps {
    bracket: BracketType;
    teams: Team[];
    isAdmin?: boolean;
    showPlaceholders?: boolean;
    onScoreUpdate?: (matchId: string, team1Score: number, team2Score: number) => void;
}

export function EliminationBracket({
    bracket,
    teams,
    isAdmin = false,
    showPlaceholders = false,
    onScoreUpdate,
}: EliminationBracketProps) {
    const champion = bracket.champion ? teams.find(t => t.id === bracket.champion) : null;

    // Determine connector type based on bracket position
    // Odd positions (1, 3, 5...) connect down to team1 slot of next match
    // Even positions (2, 4, 6...) connect up to team2 slot of previous match
    // If it's a single match (odd one out), it just connects straight
    const getConnectorType = (match: typeof bracket.rounds[0]['matches'][0], roundMatchCount: number): 'down' | 'up' | 'straight' | 'none' => {
        const pos = match.bracketPosition || 1;

        // If match count is odd and this is the last match in the round
        // it connects straight to the next round (as team1 of an unpaired match)
        if (roundMatchCount % 2 === 1 && pos === roundMatchCount) {
            return 'straight';
        }

        // Standard pairing: odd positions go down, even positions go up
        return pos % 2 === 1 ? 'down' : 'up';
    };

    return (
        <div className="elimination-bracket">
            <h2 className="bracket-title">
                <Trophy size={24} />
                Elimination Bracket
            </h2>

            <div className="bracket-container">
                {bracket.rounds.map((round, roundIndex) => {

                    return (
                        <div key={round.roundNumber} className="bracket-round">
                            <h3 className="round-title">{round.name}</h3>
                            <div className="round-matches">
                                {round.matches.map((match) => {
                                    const connectorType = roundIndex < bracket.rounds.length - 1
                                        ? getConnectorType(match, round.matches.length)
                                        : 'none';

                                    return (
                                        <div key={match.id} className="bracket-match-wrapper">
                                            <MatchCard
                                                match={showPlaceholders ? { ...match, team1Id: '', team2Id: '', team1Score: null, team2Score: null, winnerId: null, status: 'pending' } : match}
                                                teams={teams}
                                                isAdmin={isAdmin && !showPlaceholders}
                                                onScoreUpdate={onScoreUpdate}
                                            />
                                            {/* Add connectors for all except the last round (Finals) */}
                                            {connectorType !== 'none' && (
                                                <div className={`match-connector connector-${connectorType}`}>
                                                    <div className="connector-line"></div>
                                                    <div className="connector-elbow"></div>
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    );
                })}
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

        </div>
    );
}
