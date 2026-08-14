import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Header } from '../components/Header';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';
import { PoolStandings } from '../components/PoolStandings';
import { MatchCard } from '../components/MatchCard';
import { EliminationBracket } from '../components/EliminationBracket';
import { QRCodeShare } from '../components/QRCodeShare';
import { TournamentTabs } from '../components/TournamentTabs';
import {
    getEventById,
    getAllEvents,
    updateMatchScore,
    recordForfeit,
    getAllTournaments,
} from '../api';
import { Event, Tournament } from '../api/types';
import { useEventSubscription } from '../hooks/useEventSubscription';
import { Share2, RefreshCw, ChevronDown, Calendar } from 'lucide-react';
import './AdminDashboard.css';

export function AdminDashboard() {
    const [searchParams] = useSearchParams();
    const eventId = searchParams.get('id');

    const [events, setEvents] = useState<Event[]>([]);
    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [expandedTournaments, setExpandedTournaments] = useState<Set<string>>(new Set());
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
    const [shareTournamentId, setShareTournamentId] = useState<string | null>(null);
    const [showShareModal, setShowShareModal] = useState(false);
    const [loading, setLoading] = useState(true);
    const [hasThirdPlaceMatch, setHasThirdPlaceMatch] = useState(false);

    const loadSidebarData = useCallback(async () => {
        const [eventsRes, tournamentsRes] = await Promise.all([
            getAllEvents(),
            getAllTournaments()
        ]);
        if (eventsRes.success && eventsRes.data) {
            setEvents(eventsRes.data);
        }
        if (tournamentsRes.success && tournamentsRes.data) {
            setTournaments(tournamentsRes.data);
        }
    }, []);

    const loadSelectedEvent = useCallback(async (id: string) => {
        const response = await getEventById(id);
        if (response.success && response.data) {
            setSelectedEvent(response.data);
        }
    }, []);

    useEffect(() => {
        const init = async () => {
            setLoading(true);
            await loadSidebarData();
            if (eventId) {
                await loadSelectedEvent(eventId);
            }
            setLoading(false);
        };
        init();
    }, [eventId, loadSidebarData, loadSelectedEvent]);

    // Subscribe to live updates
    useEventSubscription(selectedEvent, (updated) => {
        setSelectedEvent(updated);
    });

    const handleScoreUpdate = async (matchId: string, team1Score: number, team2Score: number) => {
        if (!selectedEvent) return;

        const response = await updateMatchScore(selectedEvent.id, {
            matchId,
            team1Score,
            team2Score,
        });

        if (response.success) {
            setSelectedEvent(response.data);
            // Also update the events list
            setEvents(prev =>
                prev.map(e => e.id === response.data.id ? response.data : e)
            );
        }
    };

    const handleForfeit = async (matchId: string, winnerId: string, status: 'FORFEIT' | 'WALKOVER') => {
        if (!selectedEvent) return;

        const response = await recordForfeit(selectedEvent.id, matchId, { winnerId, status });

        if (response.success) {
            setSelectedEvent(response.data);
            setEvents(prev =>
                prev.map(e => e.id === response.data.id ? response.data : e)
            );
        }
    };

    const handleSelectEvent = async (id: string) => {
        await loadSelectedEvent(id);
        window.history.replaceState({}, '', `/admin?id=${id}`);
    };

    const getStatusLabel = (status: Event['status']) => {
        switch (status) {
            case 'SETUP': return 'Setup';
            case 'POOL_PLAY': return 'Pool Play';
            case 'ELIMINATION': return 'Playoffs';
            case 'COMPLETED': return 'Complete';
            default: return status;
        }
    };

    const toggleTournamentExpand = (tournamentId: string) => {
        setExpandedTournaments(prev => {
            const next = new Set(prev);
            if (next.has(tournamentId)) {
                next.delete(tournamentId);
            } else {
                next.add(tournamentId);
            }
            return next;
        });
    };

    const getEventsForTournament = (tournamentId: string) => {
        return events.filter(e => e.tournamentId === tournamentId);
    };

    if (loading) {
        return (
            <div className="admin-page">
                <Header />
                <main className="admin-content">
                    <LoadingState message="Loading tournaments..." />
                </main>
            </div>
        );
    }

    return (
        <div className="admin-page">
            <Header />

            <main className="admin-content">
                <aside className="tournaments-sidebar">
                    <div className="sidebar-header">
                        <h2>Tournaments</h2>
                        <Link to="/tournaments" className="manage-events-btn">
                            <Calendar size={16} />
                        </Link>
                    </div>

                    {tournaments.length === 0 ? (
                        <EmptyState
                            className="empty-state--compact"
                            description="No tournaments yet"
                            action={
                                <Link to="/tournaments" className="create-link">Create your first tournament</Link>
                            }
                        />
                    ) : (
                        <ul className="event-list">
                            {tournaments.map(tournament => (
                                <li key={tournament.id} className="event-item">
                                    <button
                                        className={`event-header-btn ${expandedTournaments.has(tournament.id) ? 'expanded' : ''}`}
                                        onClick={() => toggleTournamentExpand(tournament.id)}
                                    >
                                        <ChevronDown
                                            size={16}
                                            className={`expand-icon ${expandedTournaments.has(tournament.id) ? 'rotated' : ''}`}
                                        />
                                        <span className="event-name">{tournament.name}</span>
                                        <span className="tournament-count">
                                            {getEventsForTournament(tournament.id).length}
                                        </span>
                                    </button>

                                    {expandedTournaments.has(tournament.id) && (
                                        <ul className="tournament-dropdown">
                                            {getEventsForTournament(tournament.id).length === 0 ? (
                                                <li className="no-tournaments">
                                                    <Link to={`/setup?tournamentId=${tournament.id}`}>+ Add event</Link>
                                                </li>
                                            ) : (
                                                getEventsForTournament(tournament.id).map(e => (
                                                    <li
                                                        key={e.id}
                                                        className={`tournament-item ${selectedEvent?.id === e.id ? 'active' : ''}`}
                                                    >
                                                        <button
                                                            className="tournament-select-btn"
                                                            onClick={() => handleSelectEvent(e.id)}
                                                        >
                                                            <span className="tournament-name">{e.name}</span>
                                                            <span className={`tournament-status status-${e.status}`}>
                                                                {getStatusLabel(e.status)}
                                                            </span>
                                                        </button>
                                                    </li>
                                                ))
                                            )}
                                        </ul>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}
                </aside>

                <div className="tournament-detail">
                    {!selectedEvent ? (
                        <EmptyState
                            className="empty-state--fill"
                            title="Select an event"
                            description="Choose an event from the sidebar or go to Tournaments to create one"
                            action={
                                <Link to="/tournaments" className="create-btn-large">
                                    <Calendar size={20} />
                                    Go to Tournaments
                                </Link>
                            }
                        />
                    ) : (
                        <>
                            <div className="detail-header">
                                <div className="header-info">
                                    <h1>{selectedEvent.name}</h1>
                                    <span className={`status-badge status-${selectedEvent.status}`}>
                                        {getStatusLabel(selectedEvent.status)}
                                    </span>
                                </div>
                                <div className="header-actions">
                                    <button
                                        className="action-btn refresh"
                                        onClick={() => loadSelectedEvent(selectedEvent.id)}
                                    >
                                        <RefreshCw size={16} />
                                    </button>
                                    <button
                                        className="action-btn share"
                                        onClick={() => {
                                            setShareTournamentId(selectedEvent.tournamentId);
                                            setShowShareModal(true);
                                        }}
                                    >
                                        <Share2 size={16} />
                                        Share Tournament
                                    </button>
                                </div>
                            </div>

                            <TournamentTabs
                                hasPoolPlay={selectedEvent.pools.length > 0}
                                hasPlayoffs={!!selectedEvent.eliminationBracket}
                            >
                                {{
                                    poolPlay: (
                                        <section className="pools-section">
                                            {selectedEvent.pools.map(pool => (
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
                                                                        teams={selectedEvent.teams}
                                                                        isAdmin={true}
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
                                            {selectedEvent.eliminationBracket ? (
                                                <EliminationBracket
                                                    bracket={selectedEvent.eliminationBracket}
                                                    teams={selectedEvent.teams}
                                                    isAdmin={true}
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
                    )}
                </div>
            </main>

            {/* Share Modal */}
            {showShareModal && shareTournamentId && (
                <div className="modal-overlay" onClick={() => setShowShareModal(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <button className="modal-close" onClick={() => setShowShareModal(false)}>×</button>
                        <QRCodeShare
                            url={`${window.location.origin}/view/tournament/${shareTournamentId}`}
                            tournamentName={tournaments.find(t => t.id === shareTournamentId)?.name || 'Tournament'}
                        />
                    </div>
                </div>
            )}
        </div>
    );
}
