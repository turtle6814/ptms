import { useState } from 'react';
import { Match, Team } from '../api/types';
import { Edit2, Check, X } from 'lucide-react';
import './MatchCard.css';

interface MatchCardProps {
    match: Match;
    teams: Team[];
    isAdmin?: boolean;
    poolTeamIds?: string[]; // For calculating seed positions
    onScoreUpdate?: (matchId: string, team1Score: number, team2Score: number) => void;
}

export function MatchCard({ match, teams, isAdmin = false, poolTeamIds, onScoreUpdate }: MatchCardProps) {
    const [isEditing, setIsEditing] = useState(false);
    const [team1Score, setTeam1Score] = useState<string>(match.team1Score?.toString() ?? '');
    const [team2Score, setTeam2Score] = useState<string>(match.team2Score?.toString() ?? '');

    const team1 = teams.find(t => t.id === match.team1Id);
    const team2 = teams.find(t => t.id === match.team2Id);

    const handleSave = () => {
        const score1 = parseInt(team1Score) || 0;
        const score2 = parseInt(team2Score) || 0;
        onScoreUpdate?.(match.id, score1, score2);
        setIsEditing(false);
    };

    const handleCancel = () => {
        setTeam1Score(match.team1Score?.toString() ?? '');
        setTeam2Score(match.team2Score?.toString() ?? '');
        setIsEditing(false);
    };

    const getStatusBadge = () => {
        switch (match.status) {
            case 'completed':
                return <span className="match-status completed">Final</span>;
            case 'in_progress':
                return <span className="match-status live">Live</span>;
            default:
                return <span className="match-status pending">Upcoming</span>;
        }
    };

    // Handle TBD teams in elimination bracket
    const team1Name = team1?.name || (match.team1Id ? 'Unknown' : 'TBD');
    const team2Name = team2?.name || (match.team2Id ? 'Unknown' : 'TBD');
    const isTBD = !match.team1Id || !match.team2Id;

    // Calculate seed positions (1-based) for Berger Table display
    const getMatchOrder = () => {
        if (!poolTeamIds) return null;
        const seed1 = poolTeamIds.indexOf(match.team1Id) + 1;
        const seed2 = poolTeamIds.indexOf(match.team2Id) + 1;
        if (seed1 > 0 && seed2 > 0) {
            return `${seed1}-${seed2}`;
        }
        return null;
    };
    const matchOrder = getMatchOrder();

    return (
        <div className={`match-card ${match.status}`}>
            <div className="match-header">
                <div className="match-meta">
                    {matchOrder && <span className="match-order">{matchOrder}</span>}
                    {getStatusBadge()}
                </div>
                {isAdmin && !isEditing && match.team1Id && match.team2Id && (
                    <button className="edit-btn" onClick={() => setIsEditing(true)}>
                        <Edit2 size={14} />
                    </button>
                )}
            </div>

            <div className="teams-container">
                <div className={`team-row ${match.winnerId === match.team1Id ? 'winner' : ''}`}>
                    <span className="team-name">{team1Name}</span>
                    {isEditing ? (
                        <input
                            type="number"
                            className="score-input"
                            value={team1Score}
                            onChange={(e) => setTeam1Score(e.target.value)}
                            min="0"
                            autoFocus
                        />
                    ) : (
                        <span className="team-score">
                            {match.team1Score !== null ? match.team1Score : '-'}
                        </span>
                    )}
                </div>

                <div className="vs-divider">VS</div>

                <div className={`team-row ${match.winnerId === match.team2Id ? 'winner' : ''}`}>
                    <span className="team-name">{team2Name}</span>
                    {isEditing ? (
                        <input
                            type="number"
                            className="score-input"
                            value={team2Score}
                            onChange={(e) => setTeam2Score(e.target.value)}
                            min="0"
                        />
                    ) : (
                        <span className="team-score">
                            {match.team2Score !== null ? match.team2Score : '-'}
                        </span>
                    )}
                </div>
            </div>

            {isEditing && (
                <div className="edit-actions">
                    <button className="save-btn" onClick={handleSave}>
                        <Check size={16} /> Save
                    </button>
                    <button className="cancel-btn" onClick={handleCancel}>
                        <X size={16} /> Cancel
                    </button>
                </div>
            )}

            {isTBD && !isEditing && (
                <div className="tbd-message">Waiting for previous match results</div>
            )}
        </div>
    );
}
