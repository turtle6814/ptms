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

    return (
        <div className="elimination-bracket">
            <h2 className="bracket-title">
                <Trophy size={24} />
                Elimination Bracket
            </h2>

            {champion && (
                <div className="champion-banner">
                    <div className="champion-trophy">🏆</div>
                    <div className="champion-text">
                        <span className="champion-label">Champion</span>
                        <span className="champion-name">{champion.name}</span>
                    </div>
                </div>
            )}

            <div className="bracket-container">
                {bracket.rounds.map((round, roundIndex) => (
                    <div key={round.roundNumber} className="bracket-round">
                        <h3 className="round-title">{round.name}</h3>
                        <div className="round-matches">
                            {round.matches.map((match, index) => (
                                <div key={match.id} className="bracket-match-wrapper">
                                    <MatchCard
                                        match={showPlaceholders ? { ...match, team1Id: '', team2Id: '', team1Score: null, team2Score: null, winnerId: null, status: 'pending' } : match}
                                        teams={teams}
                                        isAdmin={isAdmin && !showPlaceholders}
                                        onScoreUpdate={onScoreUpdate}
                                    />
                                    {/* Add connectors for all except the last round (Finals) */}
                                    {roundIndex < bracket.rounds.length - 1 && (
                                        <div className={`match-connector ${index % 2 === 0 ? 'connector-down' : 'connector-up'}`}>
                                            <div className="connector-line"></div>
                                            <div className="connector-elbow"></div>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
