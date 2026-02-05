import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { Header } from '../components/Header';
import { PoolStandings } from '../components/PoolStandings';
import { MatchCard } from '../components/MatchCard';
import { EliminationBracket } from '../components/EliminationBracket';
import { TournamentTabs } from '../components/TournamentTabs';
import { getTournament, subscribeTournament, pollTournament } from '../api/mockApi';
import { Tournament } from '../api/types';
import { RefreshCw, Wifi } from 'lucide-react';
import './ViewerPage.css';

export function ViewerPage() {
    const { tournamentId } = useParams<{ tournamentId: string }>();
    const [tournament, setTournament] = useState<Tournament | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

    const loadTournament = useCallback(async () => {
        if (!tournamentId) {
            setError('No tournament ID provided');
            setLoading(false);
            return;
        }

        const response = await getTournament(tournamentId);
        if (response.success && response.data) {
            setTournament(response.data);
            setLastUpdated(new Date());
            setError('');
        } else {
            setError(response.error || 'Tournament not found');
        }
        setLoading(false);
    }, [tournamentId]);

    useEffect(() => {
        loadTournament();
    }, [loadTournament]);

    // Subscribe to live updates
    useEffect(() => {
        if (tournamentId) {
            const unsubscribe = subscribeTournament(tournamentId, (updated) => {
                setTournament(updated);
                setLastUpdated(new Date());
            });
            return unsubscribe;
        }
    }, [tournamentId]);

    // Poll for updates (simulates real-time for viewers)
    useEffect(() => {
        if (!tournamentId) return;

        const interval = setInterval(async () => {
            const response = await pollTournament(tournamentId);
            if (response.success && response.data) {
                setTournament(response.data);
                setLastUpdated(new Date());
            }
        }, 5000); // Poll every 5 seconds

        return () => clearInterval(interval);
    }, [tournamentId]);

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
            <div className="viewer-page">
                <Header />
                <main className="viewer-content">
                    <div className="loading-state">
                        <RefreshCw className="spin" size={32} />
                        <p>Loading tournament...</p>
                    </div>
                </main>
            </div>
        );
    }

    if (error || !tournament) {
        return (
            <div className="viewer-page">
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
        <div className="viewer-page">
            <Header tournamentName={tournament.name} />

            <main className="viewer-content">
                <div className="viewer-header">
                    <div className="tournament-info">
                        <h1>{tournament.name}</h1>
                        <div className="meta-badges">
                            <span className={`status-badge status-${tournament.status}`}>
                                {getStatusLabel(tournament.status)}
                            </span>
                            {tournament.status !== 'completed' && (
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

                <TournamentTabs
                    hasPoolPlay={tournament.pools.length > 0}
                    hasPlayoffs={!!tournament.eliminationBracket}
                >
                    {{
                        poolPlay: (
                            <section className="viewer-section">
                                {tournament.pools.map(pool => (
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
                                                            teams={tournament.teams}
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
                        playoffs: (
                            <section className="viewer-section">
                                {tournament.eliminationBracket ? (
                                    <EliminationBracket
                                        bracket={tournament.eliminationBracket}
                                        teams={tournament.teams}
                                        isAdmin={false}
                                    />
                                ) : (
                                    <div className="empty-bracket-message">
                                        <div className="empty-icon">🏆</div>
                                        <h3>Playoffs Not Started</h3>
                                        <p>The elimination bracket will appear here once pool play is complete.</p>
                                    </div>
                                )}
                            </section>
                        )
                    }}
                </TournamentTabs>

                {/* Tournament Complete Message */}
                {tournament.status === 'completed' && tournament.eliminationBracket?.champion && (
                    <div className="completion-message">
                        <p>🎉 Tournament Complete! Congratulations to the champion!</p>
                    </div>
                )}
            </main>
        </div>
    );
}
