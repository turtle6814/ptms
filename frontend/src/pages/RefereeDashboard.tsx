import { useEffect, useState } from 'react';
import { Header } from '../components/Header';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';
import { EventScoringPanel } from '../components/EventScoringPanel';
import { getMyAssignedEvents } from '../api';
import { Event } from '../api/types';
import { getStatusLabel, getStatusColor } from '../utils/eventStatus';
import './AdminDashboard.css';

export function RefereeDashboard() {
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const init = async () => {
            const response = await getMyAssignedEvents();
            if (response.success) {
                setEvents(response.data);
            }
            setLoading(false);
        };
        init();
    }, []);

    const handleEventUpdate = (updated: Event) => {
        setSelectedEvent(updated);
        setEvents(prev => prev.map(e => e.id === updated.id ? updated : e));
    };

    if (loading) {
        return (
            <div className="admin-page">
                <Header />
                <main className="admin-content">
                    <LoadingState message="Loading your assigned events..." />
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
                        <h2>Assigned Events</h2>
                    </div>

                    {events.length === 0 ? (
                        <EmptyState
                            className="empty-state--compact"
                            description="No events assigned to you yet"
                        />
                    ) : (
                        <ul className="event-list">
                            {events.map(event => (
                                <li
                                    key={event.id}
                                    className={`tournament-item ${selectedEvent?.id === event.id ? 'active' : ''}`}
                                >
                                    <button
                                        className="tournament-select-btn"
                                        onClick={() => setSelectedEvent(event)}
                                    >
                                        <span className="tournament-name">{event.name}</span>
                                        <span className={`tournament-status ${getStatusColor(event.status)}`}>
                                            {getStatusLabel(event.status)}
                                        </span>
                                    </button>
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
                            description="Choose an event from the sidebar to start scoring"
                        />
                    ) : (
                        <EventScoringPanel
                            event={selectedEvent}
                            canEdit={true}
                            onEventUpdate={handleEventUpdate}
                        />
                    )}
                </div>
            </main>
        </div>
    );
}
