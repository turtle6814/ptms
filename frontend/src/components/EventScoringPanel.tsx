import { ReactNode, useState } from 'react';
import { PoolStandings } from './PoolStandings';
import { MatchCard } from './MatchCard';
import { EliminationBracket } from './EliminationBracket';
import { TournamentTabs } from './TournamentTabs';
import { getEventById, updateMatchScore, recordForfeit } from '../api';
import { Event } from '../api/types';
import { getStatusLabel, getStatusColor } from '../utils/eventStatus';
import { RefreshCw } from 'lucide-react';
import { useEventSubscription } from '../hooks/useEventSubscription';

interface EventScoringPanelProps {
    event: Event;
    canEdit: boolean;
    onEventUpdate: (updated: Event) => void;
    headerActions?: ReactNode;
}

export function EventScoringPanel({ event, canEdit, onEventUpdate, headerActions }: EventScoringPanelProps) {
    const [hasThirdPlaceMatch, setHasThirdPlaceMatch] = useState(false);

    useEventSubscription(event, onEventUpdate);

    const handleRefresh = async () => {
        const response = await getEventById(event.id);
        if (response.success) {
            onEventUpdate(response.data);
        }
    };

    const handleScoreUpdate = async (matchId: string, team1Score: number, team2Score: number) => {
        const response = await updateMatchScore(event.id, { matchId, team1Score, team2Score });
        if (response.success) {
            onEventUpdate(response.data);
        }
    };

    const handleForfeit = async (matchId: string, winnerId: string, status: 'FORFEIT' | 'WALKOVER') => {
        const response = await recordForfeit(event.id, matchId, { winnerId, status });
        if (response.success) {
            onEventUpdate(response.data);
        }
    };

    return (
        <>
            <div className="detail-header">
                <div className="header-info">
                    <h1>{event.name}</h1>
                    <span className={`status-badge ${getStatusColor(event.status)}`}>
                        {getStatusLabel(event.status)}
                    </span>
                </div>
                <div className="header-actions">
                    <button className="action-btn refresh" onClick={handleRefresh}>
                        <RefreshCw size={16} />
                    </button>
                    {headerActions}
                </div>
            </div>

            <TournamentTabs
                hasPoolPlay={event.pools.length > 0}
                hasPlayoffs={!!event.eliminationBracket}
            >
                {{
                    poolPlay: (
                        <section className="pools-section">
                            {event.pools.map(pool => (
                                <div key={pool.id} className="pool-container">
                                    <div className="pool-grid">
                                        <PoolStandings
                                            poolName={pool.name}
                                            standings={pool.standings}
                                            highlightTop={2}
                                            showQualifyBadge={false}
                                        />

                                        <div className="pool-matches">
                                            <h4>Matches</h4>
                                            <div className="matches-grid">
                                                {pool.matches.map((match) => (
                                                    <MatchCard
                                                        key={match.id}
                                                        match={match}
                                                        teams={event.teams}
                                                        isAdmin={canEdit}
                                                        poolTeamIds={pool.teamIds}
                                                        onScoreUpdate={handleScoreUpdate}
                                                        onForfeit={handleForfeit}
                                                    />
                                                ))}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </section>
                    ),
                    playoffs: (
                        <section className="elimination-section">
                            {event.eliminationBracket ? (
                                <EliminationBracket
                                    bracket={event.eliminationBracket}
                                    teams={event.teams}
                                    isAdmin={canEdit}
                                    hasThirdPlaceMatch={hasThirdPlaceMatch}
                                    onThirdPlaceToggle={setHasThirdPlaceMatch}
                                    onScoreUpdate={handleScoreUpdate}
                                    onForfeit={handleForfeit}
                                />
                            ) : (
                                <div className="empty-bracket-message">
                                    <div className="empty-icon">🏆</div>
                                    <h3>Playoffs Not Started</h3>
                                    <p>Complete all pool matches to generate the elimination bracket.</p>
                                </div>
                            )}
                        </section>
                    )
                }}
            </TournamentTabs>
        </>
    );
}
