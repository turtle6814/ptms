import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Calendar, Trophy, Plus, Trash2, Edit2, X, Check } from 'lucide-react';
import { Event, Tournament } from '../api/types';
import { getEventById, updateEvent, getEventTournaments, removeTournamentFromEvent, deleteEvent } from '../api/mockApi';
import { Header } from '../components/Header';
import './EventDetailPage.css';

export function EventDetailPage() {
    const { eventId } = useParams<{ eventId: string }>();
    const navigate = useNavigate();

    const [event, setEvent] = useState<Event | null>(null);
    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isEditing, setIsEditing] = useState(false);
    const [editName, setEditName] = useState('');
    const [editDescription, setEditDescription] = useState('');

    useEffect(() => {
        if (eventId) {
            loadEventData();
        }
    }, [eventId]);

    const loadEventData = async () => {
        if (!eventId) return;

        setIsLoading(true);
        const [eventRes, tournamentsRes] = await Promise.all([
            getEventById(eventId),
            getEventTournaments(eventId)
        ]);

        if (eventRes.success && eventRes.data) {
            setEvent(eventRes.data);
            setEditName(eventRes.data.name);
            setEditDescription(eventRes.data.description || '');
        }
        if (tournamentsRes.success && tournamentsRes.data) {
            setTournaments(tournamentsRes.data);
        }
        setIsLoading(false);
    };

    const handleSaveEdit = async () => {
        if (!eventId || !editName.trim()) return;

        const result = await updateEvent(eventId, {
            name: editName.trim(),
            description: editDescription.trim() || undefined,
        });

        if (result.success && result.data) {
            setEvent(result.data);
            setIsEditing(false);
        }
    };

    const handleRemoveTournament = async (tournamentId: string) => {
        if (!eventId) return;
        if (!confirm('Remove this tournament from the event?')) return;

        const result = await removeTournamentFromEvent(eventId, tournamentId);
        if (result.success) {
            setTournaments(prev => prev.filter(t => t.id !== tournamentId));
            setEvent(result.data!);
        }
    };

    const handleDeleteEvent = async () => {
        if (!eventId) return;
        if (!confirm('Delete this event?')) return;

        const result = await deleteEvent(eventId);
        if (result.success) {
            navigate('/events');
        }
    };

    const getStatusColor = (status: Tournament['status']) => {
        switch (status) {
            case 'pool_play': return 'status-pool';
            case 'elimination': return 'status-elimination';
            case 'completed': return 'status-completed';
            default: return 'status-setup';
        }
    };

    if (isLoading) {
        return (
            <div className="event-detail-page">
                <Header />
                <div className="loading-state">Loading event...</div>
            </div>
        );
    }

    if (!event) {
        return (
            <div className="event-detail-page">
                <Header />
                <div className="error-state">
                    <h2>Event Not Found</h2>
                    <Link to="/events" className="back-link">← Back to Events</Link>
                </div>
            </div>
        );
    }

    return (
        <div className="event-detail-page">
            <Header />

            <main className="event-detail-content">
                {/* Back Navigation */}
                <Link to="/events" className="back-nav">
                    <ArrowLeft size={20} />
                    Back to Events
                </Link>

                {/* Event Header */}
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
                                    placeholder="Event name"
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
                                <h1>{event.name}</h1>
                                {event.description && <p className="event-description">{event.description}</p>}
                                <div className="event-meta">
                                    <span className="tournament-count">
                                        <Trophy size={16} />
                                        {tournaments.length} tournament{tournaments.length !== 1 ? 's' : ''}
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
                            <button className="btn-icon btn-danger" onClick={handleDeleteEvent}>
                                <Trash2 size={18} />
                            </button>
                        </div>
                    )}
                </div>

                {/* Tournaments Section */}
                <section className="tournaments-section">
                    <div className="section-header">
                        <h2>
                            <Trophy size={20} />
                            Tournaments
                        </h2>
                        <Link to={`/setup?eventId=${eventId}`} className="add-tournament-btn">
                            <Plus size={18} />
                            Add Tournament
                        </Link>
                    </div>

                    {tournaments.length === 0 ? (
                        <div className="empty-tournaments">
                            <Trophy size={48} />
                            <h3>No Tournaments Yet</h3>
                            <p>Create your first tournament for this event</p>
                            <Link to={`/setup?eventId=${eventId}`} className="create-tournament-btn">
                                Create Tournament
                            </Link>
                        </div>
                    ) : (
                        <div className="tournaments-grid">
                            {tournaments.map(tournament => (
                                <div key={tournament.id} className="tournament-card">
                                    <Link to={`/admin`} className="tournament-card-content">
                                        <div className="tournament-info">
                                            <h3>{tournament.name}</h3>
                                            <div className="tournament-meta">
                                                <span className={`status-badge ${getStatusColor(tournament.status)}`}>
                                                    {tournament.status.replace('_', ' ')}
                                                </span>
                                                <span className="team-count">
                                                    {tournament.teams.length} teams
                                                </span>
                                                <span className="pool-count">
                                                    {tournament.pools.length} pools
                                                </span>
                                            </div>
                                        </div>
                                        {/* <ExternalLink size={18} className="external-icon" /> */}
                                    </Link>
                                    <button
                                        className="remove-tournament-btn"
                                        onClick={() => handleRemoveTournament(tournament.id)}
                                        title="Remove from event"
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
