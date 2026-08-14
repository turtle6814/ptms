import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { Header } from '../components/Header';
import { PoolStandings } from '../components/PoolStandings';
import { MatchCard } from '../components/MatchCard';
import { EliminationBracket } from '../components/EliminationBracket';
import { TournamentTabs } from '../components/TournamentTabs';
import { getTournamentById, getTournamentEvents, pollEvent } from '../api';
import { Event, Tournament } from '../api/types';
import { useEventSubscription } from '../hooks/useEventSubscription';
import { LoadingState } from '../components/LoadingState';
import { Wifi, ChevronDown, Trophy } from 'lucide-react';
import './TournamentViewerPage.css';

export function TournamentViewerPage() {
    const { tournamentId } = useParams<{ tournamentId: string }>();
    const [tournament, setTournament] = useState<Tournament | null>(null);
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
    const [dropdownOpen, setDropdownOpen] = useState(false);

    const loadTournament = useCallback(async () => {
        if (!tournamentId) {
            setError('No tournament ID provided');
            setLoading(false);
            return;
        }

        const [tournamentRes, eventsRes] = await Promise.all([
            getTournamentById(tournamentId),
            getTournamentEvents(tournamentId)
        ]);

        if (tournamentRes.success) {
            setTournament(tournamentRes.data);
            setError('');
        } else {
            setError(tournamentRes.error || 'Tournament not found');
            setLoading(false);
            return;
        }

        if (eventsRes.success) {
            setEvents(eventsRes.data);
            // Auto-select first event and fetch full data
            if (eventsRes.data.length > 0) {
                const firstEvent = eventsRes.data[0];
                setSelectedEvent(firstEvent);

                // Immediately fetch full event data (pools, bracket, etc.)
                const fullData = await pollEvent(firstEvent.id);
                if (fullData.success) {
                    setSelectedEvent(fullData.data);
                    setEvents(prev => prev.map(e => e.id === fullData.data.id ? fullData.data : e));
                }
            }
            setLastUpdated(new Date());
        }
        setLoading(false);
    }, [tournamentId]);

    useEffect(() => {
        loadTournament();
    }, [loadTournament]);

    // Subscribe to live updates for selected event
    useEventSubscription(selectedEvent, (updated) => {
        setSelectedEvent(updated);
        setEvents(prev => prev.map(e => e.id === updated.id ? updated : e));
        setLastUpdated(new Date());
    });

    // Poll removed — using WebSocket (subscribeEvent) for real-time updates

    const handleSelectEvent = async (event: Event) => {
        setSelectedEvent(event); // Show immediately with whatever data we have
        setDropdownOpen(false);

        // Immediately fetch full event data (don't wait for next poll)
        const response = await pollEvent(event.id);
        if (response.success) {
            setSelectedEvent(response.data);
            setEvents(prev => prev.map(e => e.id === response.data.id ? response.data : e));
            setLastUpdated(new Date());
        }
    };

    const getStatusLabel = (status: Event['status']) => {
        switch (status) {
            case 'SETUP': return 'Setting Up';
            case 'POOL_PLAY': return 'Pool Play';
            case 'ELIMINATION': return 'Playoffs';
            case 'COMPLETED': return 'Complete';
            default: return status;
        }
    };

    if (loading) {
        return (
            <div className="event-viewer-page">
                <Header />
                <main className="viewer-content">
                    <LoadingState message="Loading tournament..." />
                </main>
            </div>
        );
    }

    if (error || !tournament) {
        return (
            <div className="event-viewer-page">
                <Header />
                <main className="viewer-content">
                    <div className="error-state">
                        <h2>Tournament Not Found</h2>
                        <p>{error || 'The tournament you\'re looking for doesn\'t exist or has been deleted.'}</p>
                    </div>
                </main>
            </div>
        );
    }

    return (
        <div className="event-viewer-page">
            <Header />

            <main className="viewer-content">
                <div className="viewer-header">
                    <div className="event-info">
                        <h1>{tournament.name}</h1>

                        {/* Event Selector Dropdown */}
                        {events.length > 0 && (
                            <div className="tournament-selector">
                                <button
                                    className={`selector-btn ${dropdownOpen ? 'open' : ''}`}
                                    onClick={() => setDropdownOpen(!dropdownOpen)}
                                >
                                    <Trophy size={16} />
                                    <span>{selectedEvent?.name || 'Select Event'}</span>
                                    {selectedEvent && (
                                        <span className={`status-badge status-${selectedEvent.status}`}>
                                            {getStatusLabel(selectedEvent.status)}
                                        </span>
                                    )}
                                    <ChevronDown size={16} className={`dropdown-arrow ${dropdownOpen ? 'open' : ''}`} />
                                </button>

                                {dropdownOpen && (
                                    <ul className="tournament-dropdown-menu">
                                        {events.map(e => (
                                            <li key={e.id}>
                                                <button
                                                    className={`dropdown-item ${selectedEvent?.id === e.id ? 'active' : ''}`}
                                                    onClick={() => handleSelectEvent(e)}
                                                >
                                                    <span className="tournament-name">{e.name}</span>
                                                    <span className={`status-badge status-${e.status}`}>
                                                        {getStatusLabel(e.status)}
                                                    </span>
                                                </button>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        )}

                        <div className="meta-badges">
                            {selectedEvent && selectedEvent.status !== 'COMPLETED' && (
                                <span className="live-badge">
                                    <Wifi size={12} />
                                    Live
                                </span>
                            )}
                        </div>
                    </div>

                    {lastUpdated && (
                        <div className="last-updated">
                            Last updated: {lastUpdated.toLocaleTimeString()}
                        </div>
                    )}
                </div>

                {!selectedEvent ? (
                    <div className="no-tournaments">
                        <Trophy size={48} />
                        <h2>No Events</h2>
                        <p>This tournament doesn't have any events yet.</p>
                    </div>
                ) : (
                    <TournamentTabs
                        hasPoolPlay={selectedEvent.pools.length > 0}
                        hasPlayoffs={!!selectedEvent.eliminationBracket}
                    >
                        {{
                            poolPlay: (
                                <section className="viewer-section">
                                    {selectedEvent.pools.map(pool => (
                                        <div key={pool.id} className="pool-view-container">
                                            <div className="pool-view-grid">
                                                <PoolStandings
                                                    poolName={pool.name}
                                                    standings={pool.standings}
                                                    highlightTop={2}
                                                />

                                                <div className="pool-matches-view">
                                                    <h4>Match Results</h4>
                                                    <div className="matches-view-grid">
                                                        {pool.matches.map((match) => (
                                                            <MatchCard
                                                                key={match.id}
                                                                match={match}
                                                                teams={selectedEvent.teams}
                                                                isAdmin={false}
                                                                poolTeamIds={pool.teamIds}
                                                            />
                                                        ))}
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </section>
                            ),
                            playoffs: selectedEvent.eliminationBracket && (
                                <section className="viewer-section">
                                    <EliminationBracket
                                        bracket={selectedEvent.eliminationBracket}
                                        teams={selectedEvent.teams}
                                        isAdmin={false}
                                        hasThirdPlaceMatch={!!selectedEvent.eliminationBracket.thirdPlaceMatch}
                                    />
                                </section>
                            )
                        }}
                    </TournamentTabs>
                )}
            </main>
        </div>
    );
}
