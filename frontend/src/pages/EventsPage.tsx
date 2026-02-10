import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Calendar, Plus, Trophy } from 'lucide-react';
import { Event } from '../api/types';
import { getAllEvents, createEvent } from '../api';
import { Header } from '../components/Header';
import './EventsPage.css';

export function EventsPage() {
    const navigate = useNavigate();
    const [events, setEvents] = useState<Event[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [newEventName, setNewEventName] = useState('');
    const [newEventDescription, setNewEventDescription] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setIsLoading(true);
        const eventsRes = await getAllEvents();

        if (eventsRes.success && eventsRes.data) {
            setEvents(eventsRes.data);
        }
        setIsLoading(false);
    };

    const handleCreateEvent = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        if (!newEventName.trim()) {
            setError('Event name is required');
            return;
        }

        const result = await createEvent({
            name: newEventName.trim(),
            description: newEventDescription.trim() || undefined,
        });

        if (result.success && result.data) {
            setEvents(prev => [...prev, result.data!]);
            setShowCreateModal(false);
            setNewEventName('');
            setNewEventDescription('');
            // Navigate to the new event
            navigate(`/events/${result.data.id}`);
        } else {
            setError(result.error || 'Failed to create event');
        }
    };


    const getTournamentCount = (event: Event) => {
        return event.tournamentIds?.length || 0;
    };



    if (isLoading) {
        return (
            <div className="events-page">
                <Header />
                <div className="loading-state">Loading events...</div>
            </div>
        );
    }

    return (
        <div className="events-page">
            <Header />

            <main className="events-content">
                <div className="events-header">
                    <div className="header-text">
                        <h1>Events</h1>
                        <p>Manage your events and tournaments</p>
                    </div>
                    <button
                        className="create-event-btn"
                        onClick={() => setShowCreateModal(true)}
                    >
                        <Plus size={20} />
                        Create Event
                    </button>
                </div>

                {events.length === 0 ? (
                    <div className="empty-state">
                        <Calendar size={64} />
                        <h2>No Events Yet</h2>
                        <p>Create your first event to organize multiple tournaments together.</p>
                        <button
                            className="create-event-btn-large"
                            onClick={() => setShowCreateModal(true)}
                        >
                            Create Your First Event
                        </button>
                    </div>
                ) : (
                    <>
                        {/* Events List */}
                        <section className="events-section">
                            <h2 className="section-title">
                                <Calendar size={20} />
                                Your Events
                            </h2>
                            <div className="events-grid">
                                {events.map(event => (
                                    <div key={event.id} className="event-card">
                                        <Link to={`/events/${event.id}`} className="event-card-content">
                                            <div className="event-icon">
                                                <Calendar size={24} />
                                            </div>
                                            <div className="event-info">
                                                <h3>{event.name}</h3>
                                                {event.description && (
                                                    <p className="event-description">{event.description}</p>
                                                )}
                                                <div className="event-meta">
                                                    <span className="tournament-count">
                                                        <Trophy size={14} />
                                                        {getTournamentCount(event)} tournament{getTournamentCount(event) !== 1 ? 's' : ''}
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

            {/* Create Event Modal */}
            {showCreateModal && (
                <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
                    <div className="modal-content" onClick={e => e.stopPropagation()}>
                        <h2>Create New Event</h2>
                        <form onSubmit={handleCreateEvent}>
                            <div className="form-group">
                                <label htmlFor="eventName">Event Name *</label>
                                <input
                                    id="eventName"
                                    type="text"
                                    value={newEventName}
                                    onChange={e => setNewEventName(e.target.value)}
                                    placeholder="e.g., Summer Pickleball Championship 2026"
                                    autoFocus
                                />
                            </div>
                            <div className="form-group">
                                <label htmlFor="eventDescription">Description (optional)</label>
                                <textarea
                                    id="eventDescription"
                                    value={newEventDescription}
                                    onChange={e => setNewEventDescription(e.target.value)}
                                    placeholder="Describe your event..."
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
                                    Create Event
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
