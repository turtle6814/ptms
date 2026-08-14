import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Calendar, Plus, Trophy } from 'lucide-react';
import { Tournament } from '../api/types';
import { getAllTournaments, createTournament } from '../api';
import { Header } from '../components/Header';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';
import { Modal } from '../components/Modal';
import './TournamentsPage.css';

export function TournamentsPage() {
    const navigate = useNavigate();
    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newTournamentName, setNewTournamentName] = useState('');
    const [newTournamentDescription, setNewTournamentDescription] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        const init = async () => {
            setIsLoading(true);
            const tournamentsRes = await getAllTournaments();

            if (tournamentsRes.success && tournamentsRes.data) {
                setTournaments(tournamentsRes.data);
            }
            setIsLoading(false);
        };
        init();
    }, []);

    const handleCreateTournament = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        if (!newTournamentName.trim()) {
            setError('Tournament name is required');
            return;
        }

        const result = await createTournament({
            name: newTournamentName.trim(),
            description: newTournamentDescription.trim() || undefined,
        });

        if (result.success) {
            setTournaments(prev => [...prev, result.data]);
            setShowCreateModal(false);
            setNewTournamentName('');
            setNewTournamentDescription('');
            // Navigate to the new tournament
            navigate(`/tournaments/${result.data.id}`);
        } else {
            setError(result.error || 'Failed to create tournament');
        }
    };


    const getEventCount = (tournament: Tournament) => {
        return tournament.eventIds?.length || 0;
    };



    if (isLoading) {
        return (
            <div className="events-page">
                <Header />
                <LoadingState message="Loading tournaments..." />
            </div>
        );
    }

    return (
        <div className="events-page">
            <Header />

            <main className="events-content">
                <div className="events-header">
                    <div className="header-text">
                        <h1>Tournaments</h1>
                        <p>Manage your tournaments and events</p>
                    </div>
                    <button
                        className="create-event-btn"
                        onClick={() => setShowCreateModal(true)}
                    >
                        <Plus size={20} />
                        Create Tournament
                    </button>
                </div>

                {tournaments.length === 0 ? (
                    <EmptyState
                        className="empty-state--full"
                        icon={<Calendar size={64} />}
                        title="No Tournaments Yet"
                        description="Create your first tournament to organize multiple events together."
                        action={
                            <button
                                className="create-event-btn-large"
                                onClick={() => setShowCreateModal(true)}
                            >
                                Create Your First Tournament
                            </button>
                        }
                    />
                ) : (
                    <>
                        {/* Tournaments List */}
                        <section className="events-section">
                            <h2 className="section-title">
                                <Calendar size={20} />
                                Your Tournaments
                            </h2>
                            <div className="events-grid">
                                {tournaments.map(tournament => (
                                    <div key={tournament.id} className="event-card">
                                        <Link to={`/tournaments/${tournament.id}`} className="event-card-content">
                                            <div className="event-icon">
                                                <Calendar size={24} />
                                            </div>
                                            <div className="event-info">
                                                <h3>{tournament.name}</h3>
                                                {tournament.description && (
                                                    <p className="event-description">{tournament.description}</p>
                                                )}
                                                <div className="event-meta">
                                                    <span className="tournament-count">
                                                        <Trophy size={14} />
                                                        {getEventCount(tournament)} event{getEventCount(tournament) !== 1 ? 's' : ''}
                                                    </span>
                                                </div>
                                            </div>
                                        </Link>
                                    </div>
                                ))}
                            </div>
                        </section>
                    </>
                )}
            </main>

            {/* Create Tournament Modal */}
            {showCreateModal && (
                <Modal onClose={() => setShowCreateModal(false)}>
                    <h2>Create New Tournament</h2>
                    <form onSubmit={handleCreateTournament}>
                        <div className="form-group">
                            <label htmlFor="tournamentName">Tournament Name *</label>
                            <input
                                id="tournamentName"
                                type="text"
                                value={newTournamentName}
                                onChange={e => setNewTournamentName(e.target.value)}
                                placeholder="e.g., Summer Pickleball Championship 2026"
                                autoFocus
                            />
                        </div>
                        <div className="form-group">
                            <label htmlFor="tournamentDescription">Description (optional)</label>
                            <textarea
                                id="tournamentDescription"
                                value={newTournamentDescription}
                                onChange={e => setNewTournamentDescription(e.target.value)}
                                placeholder="Describe your tournament..."
                                rows={3}
                            />
                        </div>
                        {error && <p className="error-message">{error}</p>}
                        <div className="modal-actions">
                            <button
                                type="button"
                                className="btn-secondary"
                                onClick={() => setShowCreateModal(false)}
                            >
                                Cancel
                            </button>
                            <button type="submit" className="btn-primary">
                                Create Tournament
                            </button>
                        </div>
                    </form>
                </Modal>
            )}
        </div>
    );
}
