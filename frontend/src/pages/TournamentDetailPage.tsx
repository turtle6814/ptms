import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Calendar, Trophy, Plus, Trash2, Edit2, X, Check } from 'lucide-react';
import { Tournament, Event } from '../api/types';
import { getTournamentById, updateTournament, getTournamentEvents, deleteEvent, deleteTournament } from '../api';
import { Header } from '../components/Header';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';
import { getStatusLabel, getStatusColor } from '../utils/eventStatus';
import './TournamentDetailPage.css';

export function TournamentDetailPage() {
    const { tournamentId } = useParams<{ tournamentId: string }>();
    const navigate = useNavigate();

    const [tournament, setTournament] = useState<Tournament | null>(null);
    const [events, setEvents] = useState<Event[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isEditing, setIsEditing] = useState(false);
    const [editName, setEditName] = useState('');
    const [editDescription, setEditDescription] = useState('');

    useEffect(() => {
        if (tournamentId) {
            loadTournamentData();
        }
    }, [tournamentId]);

    const loadTournamentData = async () => {
        if (!tournamentId) return;

        setIsLoading(true);
        const [tournamentRes, eventsRes] = await Promise.all([
            getTournamentById(tournamentId),
            getTournamentEvents(tournamentId)
        ]);

        if (tournamentRes.success && tournamentRes.data) {
            setTournament(tournamentRes.data);
            setEditName(tournamentRes.data.name);
            setEditDescription(tournamentRes.data.description || '');
        }
        if (eventsRes.success && eventsRes.data) {
            setEvents(eventsRes.data);
        }
        setIsLoading(false);
    };

    const handleSaveEdit = async () => {
        if (!tournamentId || !editName.trim()) return;

        const result = await updateTournament(tournamentId, {
            name: editName.trim(),
            description: editDescription.trim() || undefined,
        });

        if (result.success && result.data) {
            setTournament(result.data);
            setIsEditing(false);
        }
    };

    const handleDeleteEventFromTournament = async (eventId: string) => {
        if (!confirm('Delete this event? This cannot be undone.')) return;

        const result = await deleteEvent(eventId);
        if (result.success) {
            setEvents(prev => prev.filter(e => e.id !== eventId));
        }
    };

    const handleDeleteTournament = async () => {
        if (!tournamentId) return;
        if (!confirm('Delete this tournament?')) return;

        const result = await deleteTournament(tournamentId);
        if (result.success) {
            navigate('/tournaments');
        }
    };

    if (isLoading) {
        return (
            <div className="event-detail-page">
                <Header />
                <LoadingState message="Loading tournament..." />
            </div>
        );
    }

    if (!tournament) {
        return (
            <div className="event-detail-page">
                <Header />
                <div className="error-state">
                    <h2>Tournament Not Found</h2>
                    <Link to="/tournaments" className="back-link">← Back to Tournaments</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="event-detail-page">
            <Header />

            <main className="event-detail-content">
                {/* Back Navigation */}
                <Link to="/tournaments" className="back-nav">
                    <ArrowLeft size={20} />
                    Back to Tournaments
                </Link>

                {/* Tournament Header */}
                <div className="event-header-card">
                    <div className="event-header-icon">
                        <Calendar size={32} />
                    </div>

                    <div className="event-header-content">
                        {isEditing ? (
                            <div className="edit-form">
                                <input
                                    type="text"
                                    value={editName}
                                    onChange={e => setEditName(e.target.value)}
                                    className="edit-name-input"
                                    placeholder="Tournament name"
                                    autoFocus
                                />
                                <textarea
                                    value={editDescription}
                                    onChange={e => setEditDescription(e.target.value)}
                                    className="edit-description-input"
                                    placeholder="Description (optional)"
                                    rows={2}
                                />
                                <div className="edit-actions">
                                    <button className="btn-icon btn-cancel" onClick={() => setIsEditing(false)}>
                                        <X size={18} />
                                    </button>
                                    <button className="btn-icon btn-save" onClick={handleSaveEdit}>
                                        <Check size={18} />
                                    </button>
                                </div>
                            </div>
                        ) : (
                            <>
                                <h1>{tournament.name}</h1>
                                {tournament.description && <p className="event-description">{tournament.description}</p>}
                                <div className="event-meta">
                                    <span className="tournament-count">
                                        <Trophy size={16} />
                                        {events.length} event{events.length !== 1 ? 's' : ''}
                                    </span>
                                </div>
                            </>
                        )}
                    </div>

                    {!isEditing && (
                        <div className="event-header-actions">
                            <button className="btn-icon" onClick={() => setIsEditing(true)}>
                                <Edit2 size={18} />
                            </button>
                            <button className="btn-icon btn-danger" onClick={handleDeleteTournament}>
                                <Trash2 size={18} />
                            </button>
                        </div>
                    )}
                </div>

                {/* Events Section */}
                <section className="tournaments-section">
                    <div className="section-header">
                        <h2>
                            <Trophy size={20} />
                            Events
                        </h2>
                        <Link to={`/setup?tournamentId=${tournamentId}`} className="add-tournament-btn">
                            <Plus size={18} />
                            Add Event
                        </Link>
                    </div>

                    {events.length === 0 ? (
                        <EmptyState
                            className="empty-state--card"
                            icon={<Trophy size={48} />}
                            title="No Events Yet"
                            description="Create your first event for this tournament"
                            action={
                                <Link to={`/setup?tournamentId=${tournamentId}`} className="create-tournament-btn">
                                    Create Event
                                </Link>
                            }
                        />
                    ) : (
                        <div className="tournaments-grid">
                            {events.map(event => (
                                <div key={event.id} className="tournament-card">
                                    <Link to={`/admin?id=${event.id}`} className="tournament-card-content">
                                        <div className="tournament-info">
                                            <h3>{event.name}</h3>
                                            <div className="tournament-meta">
                                                <span className={`status-badge ${getStatusColor(event.status)}`}>
                                                    {getStatusLabel(event.status)}
                                                </span>
                                                <span className="team-count">
                                                    {event.teams.length} teams
                                                </span>
                                                <span className="pool-count">
                                                    {event.pools.length} pools
                                                </span>
                                            </div>
                                        </div>
                                    </Link>
                                    <button
                                        className="remove-tournament-btn"
                                        onClick={() => handleDeleteEventFromTournament(event.id)}
                                        title="Delete event"
                                    >
                                        <Trash2 size={16} />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </section>
            </main>
        </div>
    );
}
