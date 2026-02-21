import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { Header } from '../components/Header';
import { PoolStandings } from '../components/PoolStandings';
import { MatchCard } from '../components/MatchCard';
import { EliminationBracket } from '../components/EliminationBracket';
import { TournamentTabs } from '../components/TournamentTabs';
import { getEventById, getEventTournaments, subscribeTournament, pollTournament } from '../api';
import { Tournament, Event } from '../api/types';
import { RefreshCw, Wifi, ChevronDown, Trophy } from 'lucide-react';
import './EventViewerPage.css';

export function EventViewerPage() {
    const { eventId } = useParams<{ eventId: string }>();
    const [event, setEvent] = useState<Event | null>(null);
    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [selectedTournament, setSelectedTournament] = useState<Tournament | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
    const [dropdownOpen, setDropdownOpen] = useState(false);

    const loadEvent = useCallback(async () => {
        if (!eventId) {
            setError('No event ID provided');
            setLoading(false);
            return;
        }

        const [eventRes, tournamentsRes] = await Promise.all([
            getEventById(eventId),
            getEventTournaments(eventId)
        ]);

        if (eventRes.success && eventRes.data) {
            setEvent(eventRes.data);
            setError('');
        } else {
            setError(eventRes.error || 'Event not found');
            setLoading(false);
            return;
        }

        if (tournamentsRes.success && tournamentsRes.data) {
            setTournaments(tournamentsRes.data);
            // Auto-select first tournament and fetch full data
            if (tournamentsRes.data.length > 0) {
                const firstTournament = tournamentsRes.data[0];
                setSelectedTournament(firstTournament);

                // Immediately fetch full tournament data (pools, bracket, etc.)
                const fullData = await pollTournament(firstTournament.id);
                if (fullData.success && fullData.data) {
                    setSelectedTournament(fullData.data);
                    setTournaments(prev => prev.map(t => t.id === fullData.data!.id ? fullData.data! : t));
                }
            }
            setLastUpdated(new Date());
        }
        setLoading(false);
    }, [eventId]);

    useEffect(() => {
        loadEvent();
    }, [loadEvent]);

    // Subscribe to live updates for selected tournament
    useEffect(() => {
        if (selectedTournament) {
            const unsubscribe = subscribeTournament(selectedTournament.id, (updated) => {
                setSelectedTournament(updated);
                setTournaments(prev => prev.map(t => t.id === updated.id ? updated : t));
                setLastUpdated(new Date());
            });
            return unsubscribe;
        }
    }, [selectedTournament?.id]);

    // Poll removed — using WebSocket (subscribeTournament) for real-time updates

    const handleSelectTournament = async (tournament: Tournament) => {
        setSelectedTournament(tournament); // Show immediately with whatever data we have
        setDropdownOpen(false);

        // Immediately fetch full tournament data (don't wait for next poll)
        const response = await pollTournament(tournament.id);
        if (response.success && response.data) {
            setSelectedTournament(response.data);
            setTournaments(prev => prev.map(t => t.id === response.data!.id ? response.data! : t));
            setLastUpdated(new Date());
        }
    };

    const getStatusLabel = (status: Tournament['status']) => {
        switch (status) {
            case 'setup': return 'Setting Up';
            case 'pool_play': return 'Pool Play';
            case 'elimination': return 'Playoffs';
            case 'completed': return 'Complete';
            default: return status;
        }
    };

    if (loading) {
        return (
            <div className="event-viewer-page">
                <Header />
                <main className="viewer-content">
                    <div className="loading-state">
                        <RefreshCw className="spin" size={32} />
                        <p>Loading event...</p>
                    </div>
                </main>
            </div>
        );
    }

    if (error || !event) {
        return (
            <div className="event-viewer-page">
                <Header />
                <main className="viewer-content">
                    <div className="error-state">
                        <h2>Event Not Found</h2>
                        <p>{error || 'The event you\'re looking for doesn\'t exist or has been deleted.'}</p>
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
                        <h1>{event.name}</h1>

                        {/* Tournament Selector Dropdown */}
                        {tournaments.length > 0 && (
                            <div className="tournament-selector">
                                <button
                                    className={`selector-btn ${dropdownOpen ? 'open' : ''}`}
                                    onClick={() => setDropdownOpen(!dropdownOpen)}
                                >
                                    <Trophy size={16} />
                                    <span>{selectedTournament?.name || 'Select Tournament'}</span>
                                    {selectedTournament && (
                                        <span className={`status-badge status-${selectedTournament.status}`}>
                                            {getStatusLabel(selectedTournament.status)}
                                        </span>
                                    )}
                                    <ChevronDown size={16} className={`dropdown-arrow ${dropdownOpen ? 'open' : ''}`} />
                                </button>

                                {dropdownOpen && (
                                    <ul className="tournament-dropdown-menu">
                                        {tournaments.map(t => (
                                            <li key={t.id}>
                                                <button
                                                    className={`dropdown-item ${selectedTournament?.id === t.id ? 'active' : ''}`}
                                                    onClick={() => handleSelectTournament(t)}
                                                >
                                                    <span className="tournament-name">{t.name}</span>
                                                    <span className={`status-badge status-${t.status}`}>
                                                        {getStatusLabel(t.status)}
                                                    </span>
                                                </button>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        )}

                        <div className="meta-badges">
                            {selectedTournament && selectedTournament.status !== 'completed' && (
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

                {!selectedTournament ? (
                    <div className="no-tournaments">
                        <Trophy size={48} />
                        <h2>No Tournaments</h2>
                        <p>This event doesn't have any tournaments yet.</p>
                    </div>
                ) : (
                    <TournamentTabs
                        hasPoolPlay={selectedTournament.pools.length > 0}
                        hasPlayoffs={!!selectedTournament.eliminationBracket}
                    >
                        {{
                            poolPlay: (
                                <section className="viewer-section">
                                    {selectedTournament.pools.map(pool => (
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
                                                                teams={selectedTournament.teams}
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
                            playoffs: selectedTournament.eliminationBracket && (
                                <section className="viewer-section">
                                    <EliminationBracket
                                        bracket={selectedTournament.eliminationBracket}
                                        teams={selectedTournament.teams}
                                        isAdmin={false}
                                        hasThirdPlaceMatch={selectedTournament.hasThirdPlaceMatch}
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
