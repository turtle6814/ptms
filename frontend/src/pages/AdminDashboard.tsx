import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Header } from '../components/Header';
import { PoolStandings } from '../components/PoolStandings';
import { MatchCard } from '../components/MatchCard';
import { EliminationBracket } from '../components/EliminationBracket';
import { QRCodeShare } from '../components/QRCodeShare';
import { TournamentTabs } from '../components/TournamentTabs';
import {
    getTournament,
    getAllTournaments,
    updateMatchScore,
    subscribeTournament,
    getAllEvents,
    toggleThirdPlaceMatch,
} from '../api';
import { Tournament, Event } from '../api/types';
import { Share2, RefreshCw, ChevronDown, Calendar } from 'lucide-react';
import './AdminDashboard.css';

export function AdminDashboard() {
    const [searchParams] = useSearchParams();
    const tournamentId = searchParams.get('id');

    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [events, setEvents] = useState<Event[]>([]);
    const [expandedEvents, setExpandedEvents] = useState<Set<string>>(new Set());
    const [selectedTournament, setSelectedTournament] = useState<Tournament | null>(null);
    const [shareEventId, setShareEventId] = useState<string | null>(null);
    const [showShareModal, setShowShareModal] = useState(false);
    const [loading, setLoading] = useState(true);


    const loadTournaments = useCallback(async () => {
        const [tournamentsRes, eventsRes] = await Promise.all([
            getAllTournaments(),
            getAllEvents()
        ]);
        if (tournamentsRes.success && tournamentsRes.data) {
            setTournaments(tournamentsRes.data);
        }
        if (eventsRes.success && eventsRes.data) {
            setEvents(eventsRes.data);
        }
    }, []);

    const loadSelectedTournament = useCallback(async (id: string) => {
        const response = await getTournament(id);
        if (response.success && response.data) {
            setSelectedTournament(response.data);
        }
    }, []);

    useEffect(() => {
        const init = async () => {
            setLoading(true);
            await loadTournaments();
            if (tournamentId) {
                await loadSelectedTournament(tournamentId);
            }
            setLoading(false);
        };
        init();
    }, [tournamentId, loadTournaments, loadSelectedTournament]);

    // Subscribe to live updates
    useEffect(() => {
        if (selectedTournament) {
            const unsubscribe = subscribeTournament(selectedTournament.id, (updated) => {
                setSelectedTournament(updated);
            });
            return unsubscribe;
        }
    }, [selectedTournament?.id]);

    const handleScoreUpdate = async (matchId: string, team1Score: number, team2Score: number) => {
        if (!selectedTournament) return;

        const response = await updateMatchScore(selectedTournament.id, {
            matchId,
            team1Score,
            team2Score,
        });

        if (response.success && response.data) {
            setSelectedTournament(response.data);
            // Also update the tournaments list
            setTournaments(prev =>
                prev.map(t => t.id === response.data!.id ? response.data! : t)
            );
        }
    };

    const handleSelectTournament = async (id: string) => {
        await loadSelectedTournament(id);
        window.history.replaceState({}, '', `/admin?id=${id}`);
    };

    const getStatusLabel = (status: Tournament['status']) => {
        switch (status) {
            case 'setup': return 'Setup';
            case 'pool_play': return 'Pool Play';
            case 'elimination': return 'Playoffs';
            case 'completed': return 'Complete';
            default: return status;
        }
    };

    const toggleEventExpand = (eventId: string) => {
        setExpandedEvents(prev => {
            const next = new Set(prev);
            if (next.has(eventId)) {
                next.delete(eventId);
            } else {
                next.add(eventId);
            }
            return next;
        });
    };

    const getEventTournaments = (eventId: string) => {
        return tournaments.filter(t => t.eventId === eventId);
    };

    if (loading) {
        return (
            <div className="admin-page">
                <Header />
                <main className="admin-content">
                    <div className="loading-state">Loading tournaments...</div>
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
                        <h2>Events</h2>
                        <Link to="/events" className="manage-events-btn">
                            <Calendar size={16} />
                        </Link>
                    </div>

                    {events.length === 0 ? (
                        <div className="empty-state">
                            <p>No events yet</p>
                            <Link to="/events" className="create-link">Create your first event</Link>
                        </div>
                    ) : (
                        <ul className="event-list">
                            {events.map(event => (
                                <li key={event.id} className="event-item">
                                    <button
                                        className={`event-header-btn ${expandedEvents.has(event.id) ? 'expanded' : ''}`}
                                        onClick={() => toggleEventExpand(event.id)}
                                    >
                                        <ChevronDown
                                            size={16}
                                            className={`expand-icon ${expandedEvents.has(event.id) ? 'rotated' : ''}`}
                                        />
                                        <span className="event-name">{event.name}</span>
                                        <span className="tournament-count">
                                            {getEventTournaments(event.id).length}
                                        </span>
                                    </button>

                                    {expandedEvents.has(event.id) && (
                                        <ul className="tournament-dropdown">
                                            {getEventTournaments(event.id).length === 0 ? (
                                                <li className="no-tournaments">
                                                    <Link to={`/setup?eventId=${event.id}`}>+ Add tournament</Link>
                                                </li>
                                            ) : (
                                                getEventTournaments(event.id).map(t => (
                                                    <li
                                                        key={t.id}
                                                        className={`tournament-item ${selectedTournament?.id === t.id ? 'active' : ''}`}
                                                    >
                                                        <button
                                                            className="tournament-select-btn"
                                                            onClick={() => handleSelectTournament(t.id)}
                                                        >
                                                            <span className="tournament-name">{t.name}</span>
                                                            <span className={`tournament-status status-${t.status}`}>
                                                                {getStatusLabel(t.status)}
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
                    {!selectedTournament ? (
                        <div className="no-selection">
                            <h2>Select a tournament</h2>
                            <p>Choose a tournament from the sidebar or go to Events to create one</p>
                            <Link to="/events" className="create-btn-large">
                                <Calendar size={20} />
                                Go to Events
                            </Link>
                        </div>
                    ) : (
                        <>
                            <div className="detail-header">
                                <div className="header-info">
                                    <h1>{selectedTournament.name}</h1>
                                    <span className={`status-badge status-${selectedTournament.status}`}>
                                        {getStatusLabel(selectedTournament.status)}
                                    </span>
                                </div>
                                <div className="header-actions">
                                    <button
                                        className="action-btn refresh"
                                        onClick={() => loadSelectedTournament(selectedTournament.id)}
                                    >
                                        <RefreshCw size={16} />
                                    </button>
                                    {selectedTournament.eventId && (
                                        <button
                                            className="action-btn share"
                                            onClick={() => {
                                                setShareEventId(selectedTournament.eventId!);
                                                setShowShareModal(true);
                                            }}
                                        >
                                            <Share2 size={16} />
                                            Share Event
                                        </button>
                                    )}
                                </div>
                            </div>

                            <TournamentTabs
                                hasPoolPlay={selectedTournament.pools.length > 0}
                                hasPlayoffs={!!selectedTournament.eliminationBracket}
                            >
                                {{
                                    poolPlay: (
                                        <section className="pools-section">
                                            {selectedTournament.pools.map(pool => (
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
                                                                        teams={selectedTournament.teams}
                                                                        isAdmin={true}
                                                                        poolTeamIds={pool.teamIds}
                                                                        onScoreUpdate={handleScoreUpdate}
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
                                            {selectedTournament.eliminationBracket ? (
                                                <EliminationBracket
                                                    bracket={selectedTournament.eliminationBracket}
                                                    teams={selectedTournament.teams}
                                                    isAdmin={true}
                                                    hasThirdPlaceMatch={selectedTournament.hasThirdPlaceMatch}
                                                    onThirdPlaceToggle={async (enabled) => {
                                                        const res = await toggleThirdPlaceMatch(selectedTournament.id, enabled);
                                                        if (res.success && res.data) {
                                                            setSelectedTournament(res.data);
                                                        }
                                                    }}
                                                    onScoreUpdate={handleScoreUpdate}
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
            {showShareModal && shareEventId && (
                <div className="modal-overlay" onClick={() => setShowShareModal(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <button className="modal-close" onClick={() => setShowShareModal(false)}>×</button>
                        <QRCodeShare
                            url={`${window.location.origin}/view/event/${shareEventId}`}
                            tournamentName={events.find(e => e.id === shareEventId)?.name || 'Event'}
                        />
                    </div>
                </div>
            )}
        </div>
    );
}
