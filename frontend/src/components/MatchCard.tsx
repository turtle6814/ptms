import { useState } from 'react';
import { Match, Team } from '../api/types';
import { Edit2, Check, X, Flag } from 'lucide-react';
import './MatchCard.css';

interface MatchCardProps {
    match: Match;
    teams: Team[];
    isAdmin?: boolean;
    poolTeamIds?: string[]; // For calculating seed positions
    onScoreUpdate?: (matchId: string, team1Score: number, team2Score: number) => void;
    onForfeit?: (matchId: string, winnerId: string, status: 'FORFEIT' | 'WALKOVER') => void;
}

// Mirrors backend ScoreRules.validate — server stays authoritative, this just avoids
// an obviously-doomed round trip.
function validateScore(match: Match, team1Score: number, team2Score: number): string | null {
    if (team1Score < 0 || team2Score < 0) return 'Scores cannot be negative';
    if (team1Score === team2Score) return 'Match cannot end in a tie';

    const winner = Math.max(team1Score, team2Score);
    const loser = Math.min(team1Score, team2Score);

    if (winner > match.scoreCap) return `Winning score cannot exceed the cap of ${match.scoreCap}`;
    if (winner < match.targetScore) return `Winning score must reach at least ${match.targetScore}`;
    if (match.winByTwo && winner < match.scoreCap && winner - loser < 2) {
        return `Win by two required (unless the cap of ${match.scoreCap} is reached)`;
    }
    return null;
}

export function MatchCard({ match, teams, isAdmin = false, poolTeamIds, onScoreUpdate, onForfeit }: MatchCardProps) {
    const [isEditing, setIsEditing] = useState(false);
    const [team1Score, setTeam1Score] = useState<string>(match.team1Score?.toString() ?? '');
    const [team2Score, setTeam2Score] = useState<string>(match.team2Score?.toString() ?? '');
    const [scoreError, setScoreError] = useState<string | null>(null);
    const [isForfeiting, setIsForfeiting] = useState(false);
    const [forfeitStatus, setForfeitStatus] = useState<'FORFEIT' | 'WALKOVER'>('FORFEIT');

    const team1 = teams.find(t => t.id === match.team1Id);
    const team2 = teams.find(t => t.id === match.team2Id);

    const handleSave = () => {
        const score1 = parseInt(team1Score) || 0;
        const score2 = parseInt(team2Score) || 0;
        const validationError = validateScore(match, score1, score2);
        if (validationError) {
            setScoreError(validationError);
            return;
        }
        setScoreError(null);
        onScoreUpdate?.(match.id, score1, score2);
        setIsEditing(false);
    };

    const handleCancel = () => {
        setTeam1Score(match.team1Score?.toString() ?? '');
        setTeam2Score(match.team2Score?.toString() ?? '');
        setScoreError(null);
        setIsEditing(false);
    };

    const handleForfeit = (winnerId: string, status: 'FORFEIT' | 'WALKOVER') => {
        onForfeit?.(match.id, winnerId, status);
        setIsForfeiting(false);
    };

    const getStatusBadge = () => {
        switch (match.status) {
            case 'COMPLETED':
                return <span className="match-status completed">Final</span>;
            case 'IN_PROGRESS':
                return <span className="match-status live">Live</span>;
            case 'FORFEIT':
                return <span className="match-status completed">Forfeit</span>;
            case 'WALKOVER':
                return <span className="match-status completed">Walkover</span>;
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
                {isAdmin && !isEditing && !isForfeiting && match.team1Id && match.team2Id
                    && (match.status === 'PENDING' || match.status === 'IN_PROGRESS') && (
                    <div className="match-actions">
                        <button className="edit-btn" onClick={() => setIsEditing(true)}>
                            <Edit2 size={14} />
                        </button>
                        {onForfeit && (
                            <button className="edit-btn" title="Forfeit / Walkover" onClick={() => setIsForfeiting(true)}>
                                <Flag size={14} />
                            </button>
                        )}
                    </div>
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
                <>
                    {scoreError && <div className="error-message">{scoreError}</div>}
                    <div className="edit-actions">
                        <button className="save-btn" onClick={handleSave}>
                            <Check size={16} /> Save
                        </button>
                        <button className="cancel-btn" onClick={handleCancel}>
                            <X size={16} /> Cancel
                        </button>
                    </div>
                </>
            )}

            {isForfeiting && (
                <div className="edit-actions">
                    <select value={forfeitStatus} onChange={(e) => setForfeitStatus(e.target.value as 'FORFEIT' | 'WALKOVER')}>
                        <option value="FORFEIT">Forfeit</option>
                        <option value="WALKOVER">Walkover</option>
                    </select>
                    <button className="save-btn" onClick={() => handleForfeit(match.team1Id, forfeitStatus)}>
                        {team1Name} wins
                    </button>
                    <button className="save-btn" onClick={() => handleForfeit(match.team2Id, forfeitStatus)}>
                        {team2Name} wins
                    </button>
                    <button className="cancel-btn" onClick={() => setIsForfeiting(false)}>
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
