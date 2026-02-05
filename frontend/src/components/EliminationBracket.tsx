import { EliminationBracket as BracketType, Team } from '../api/types';
import { MatchCard } from './MatchCard';
import { Trophy } from 'lucide-react';
import './EliminationBracket.css';

interface EliminationBracketProps {
    bracket: BracketType;
    teams: Team[];
    isAdmin?: boolean;
    onScoreUpdate?: (matchId: string, team1Score: number, team2Score: number) => void;
}

export function EliminationBracket({
    bracket,
    teams,
    isAdmin = false,
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
                {bracket.rounds.map((round) => (
                    <div key={round.roundNumber} className="bracket-round">
                        <h3 className="round-title">{round.name}</h3>
                        <div className="round-matches">
                            {round.matches.map((match) => (
                                <div key={match.id} className="bracket-match-wrapper">
                                    <MatchCard
                                        match={match}
                                        teams={teams}
                                        isAdmin={isAdmin}
                                        onScoreUpdate={onScoreUpdate}
                                    />
                                </div>
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
