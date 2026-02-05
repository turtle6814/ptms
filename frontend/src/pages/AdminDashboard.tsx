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
    generateShareableLink,
    subscribeTournament,
    deleteTournament,
} from '../api/mockApi';
import { Tournament } from '../api/types';
import { Plus, Share2, Trash2, RefreshCw } from 'lucide-react';
import './AdminDashboard.css';

export function AdminDashboard() {
    const [searchParams] = useSearchParams();
    const tournamentId = searchParams.get('id');

    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [selectedTournament, setSelectedTournament] = useState<Tournament | null>(null);
    const [showShareModal, setShowShareModal] = useState(false);
    const [loading, setLoading] = useState(true);

    const loadTournaments = useCallback(async () => {
        const response = await getAllTournaments();
        if (response.success && response.data) {
            setTournaments(response.data);
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

    const handleDeleteTournament = async (id: string) => {
        if (!confirm('Are you sure you want to delete this tournament?')) return;

        await deleteTournament(id);
        await loadTournaments();
        if (selectedTournament?.id === id) {
            setSelectedTournament(null);
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

    if (loading) {
        return (
            <div className="admin-page">
                <Header isAdmin />
                <main className="admin-content">
                    <div className="loading-state">Loading tournaments...</div>
                </main>
            </div>
        );
    }

    return (
        <div className="admin-page">
            <Header
                isAdmin
                tournamentName={selectedTournament?.name}
            />

            <main className="admin-content">
                <aside className="tournaments-sidebar">
                    <div className="sidebar-header">
                        <h2>Tournaments</h2>
                        <Link to="/setup" className="new-tournament-btn">
                            <Plus size={16} />
                        </Link>
                    </div>

                    {tournaments.length === 0 ? (
                        <div className="empty-state">
                            <p>No tournaments yet</p>
                            <Link to="/setup" className="create-link">Create your first tournament</Link>
                        </div>
                    ) : (
                        <ul className="tournament-list">
                            {tournaments.map(t => (
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
                                    <button
                                        className="delete-btn"
                                        onClick={() => handleDeleteTournament(t.id)}
                                    >
                                        <Trash2 size={14} />
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </aside>

                <div className="tournament-detail">
                    {!selectedTournament ? (
                        <div className="no-selection">
                            <h2>Select a tournament</h2>
                            <p>Choose a tournament from the sidebar or create a new one</p>
                            <Link to="/setup" className="create-btn-large">
                                <Plus size={20} />
                                Create New Tournament
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
                                    <button
                                        className="action-btn share"
                                        onClick={() => setShowShareModal(true)}
                                    >
                                        <Share2 size={16} />
                                        Share
                                    </button>
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
            {showShareModal && selectedTournament && (
                <div className="modal-overlay" onClick={() => setShowShareModal(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <button className="modal-close" onClick={() => setShowShareModal(false)}>×</button>
                        <QRCodeShare
                            url={generateShareableLink(selectedTournament.id)}
                            tournamentName={selectedTournament.name}
                        />
                    </div>
                </div>
            )}
        </div>
    );
}
