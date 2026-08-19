import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Header } from '../components/Header';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';
import { QRCodeShare } from '../components/QRCodeShare';
import { Modal } from '../components/Modal';
import { EventScoringPanel } from '../components/EventScoringPanel';
import { RefereeAssignmentModal } from '../components/RefereeAssignmentModal';
import {
    getEventById,
    getAllEvents,
    getAllTournaments,
} from '../api';
import { Event, Tournament } from '../api/types';
import { getStatusLabel, getStatusColor } from '../utils/eventStatus';
import { useAuth } from '../context/useAuth';
import { Share2, Users, ChevronDown, Calendar } from 'lucide-react';
import './AdminDashboard.css';

export function AdminDashboard() {
    const { user } = useAuth();
    const canEdit = user?.role === 'ADMIN' || user?.role === 'ORGANIZER';
    const [searchParams] = useSearchParams();
    const eventId = searchParams.get('id');

    const [events, setEvents] = useState<Event[]>([]);
    const [tournaments, setTournaments] = useState<Tournament[]>([]);
    const [expandedTournaments, setExpandedTournaments] = useState<Set<string>>(new Set());
    const [selectedEvent, setSelectedEvent] = useState<Event | null>(null);
    const [shareTournamentId, setShareTournamentId] = useState<string | null>(null);
    const [showShareModal, setShowShareModal] = useState(false);
    const [showRefereeModal, setShowRefereeModal] = useState(false);
    const [loading, setLoading] = useState(true);

    const loadSidebarData = useCallback(async () => {
        const [eventsRes, tournamentsRes] = await Promise.all([
            getAllEvents(),
            getAllTournaments()
        ]);
        if (eventsRes.success && eventsRes.data) {
            setEvents(eventsRes.data);
        }
        if (tournamentsRes.success && tournamentsRes.data) {
            setTournaments(tournamentsRes.data);
        }
    }, []);

    const loadSelectedEvent = useCallback(async (id: string) => {
        const response = await getEventById(id);
        if (response.success && response.data) {
            setSelectedEvent(response.data);
        }
    }, []);

    useEffect(() => {
        const init = async () => {
            setLoading(true);
            await loadSidebarData();
            if (eventId) {
                await loadSelectedEvent(eventId);
            }
            setLoading(false);
        };
        init();
    }, [eventId, loadSidebarData, loadSelectedEvent]);

    const handleEventUpdate = (updated: Event) => {
        setSelectedEvent(updated);
        setEvents(prev => prev.map(e => e.id === updated.id ? updated : e));
    };

    const handleSelectEvent = async (id: string) => {
        await loadSelectedEvent(id);
        window.history.replaceState({}, '', `/admin?id=${id}`);
    };

    const toggleTournamentExpand = (tournamentId: string) => {
        setExpandedTournaments(prev => {
            const next = new Set(prev);
            if (next.has(tournamentId)) {
                next.delete(tournamentId);
            } else {
                next.add(tournamentId);
            }
            return next;
        });
    };

    const getEventsForTournament = (tournamentId: string) => {
        return events.filter(e => e.tournamentId === tournamentId);
    };

    if (loading) {
        return (
            <div className="admin-page">
                <Header />
                <main className="admin-content">
                    <LoadingState message="Loading tournaments..." />
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
                        <h2>Tournaments</h2>
                        <Link to="/tournaments" className="manage-events-btn">
                            <Calendar size={16} />
                        </Link>
                    </div>

                    {tournaments.length === 0 ? (
                        <EmptyState
                            className="empty-state--compact"
                            description="No tournaments yet"
                            action={
                                <Link to="/tournaments" className="create-link">Create your first tournament</Link>
                            }
                        />
                    ) : (
                        <ul className="event-list">
                            {tournaments.map(tournament => (
                                <li key={tournament.id} className="event-item">
                                    <button
                                        className={`event-header-btn ${expandedTournaments.has(tournament.id) ? 'expanded' : ''}`}
                                        onClick={() => toggleTournamentExpand(tournament.id)}
                                    >
                                        <ChevronDown
                                            size={16}
                                            className={`expand-icon ${expandedTournaments.has(tournament.id) ? 'rotated' : ''}`}
                                        />
                                        <span className="event-name">{tournament.name}</span>
                                        <span className="tournament-count">
                                            {getEventsForTournament(tournament.id).length}
                                        </span>
                                    </button>

                                    {expandedTournaments.has(tournament.id) && (
                                        <ul className="tournament-dropdown">
                                            {getEventsForTournament(tournament.id).length === 0 ? (
                                                <li className="no-tournaments">
                                                    <Link to={`/setup?tournamentId=${tournament.id}`}>+ Add event</Link>
                                                </li>
                                            ) : (
                                                getEventsForTournament(tournament.id).map(e => (
                                                    <li
                                                        key={e.id}
                                                        className={`tournament-item ${selectedEvent?.id === e.id ? 'active' : ''}`}
                                                    >
                                                        <button
                                                            className="tournament-select-btn"
                                                            onClick={() => handleSelectEvent(e.id)}
                                                        >
                                                            <span className="tournament-name">{e.name}</span>
<span className={`tournament-status ${getStatusColor(e.status)}`}>
                                                            {getStatusLabel(e.status)}
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
                    {!selectedEvent ? (
                        <EmptyState
                            className="empty-state--fill"
                            title="Select an event"
                            description="Choose an event from the sidebar or go to Tournaments to create one"
                            action={
                                <Link to="/tournaments" className="create-btn-large">
                                    <Calendar size={20} />
                                    Go to Tournaments
                                </Link>
                            }
                        />
                    ) : (
                        <EventScoringPanel
                            event={selectedEvent}
                            canEdit={canEdit}
                            onEventUpdate={handleEventUpdate}
                            headerActions={canEdit ? (
                                <>
                                    <button
                                        className="action-btn"
                                        onClick={() => setShowRefereeModal(true)}
                                    >
                                        <Users size={16} />
                                        Referees
                                    </button>
                                    <button
                                        className="action-btn share"
                                        onClick={() => {
                                            setShareTournamentId(selectedEvent.tournamentId);
                                            setShowShareModal(true);
                                        }}
                                    >
                                        <Share2 size={16} />
                                        Share Tournament
                                    </button>
                                </>
                            ) : undefined}
                        />
                    )}
                </div>
            </main>

            {/* Share Modal */}
            {showShareModal && shareTournamentId && (
                <Modal onClose={() => setShowShareModal(false)}>
                    <button className="modal-close" onClick={() => setShowShareModal(false)}>×</button>
                    <QRCodeShare
                        url={`${window.location.origin}/view/tournament/${shareTournamentId}`}
                        tournamentName={tournaments.find(t => t.id === shareTournamentId)?.name || 'Tournament'}
                    />
                </Modal>
            )}

            {/* Referee Assignment Modal */}
            {showRefereeModal && selectedEvent && (
                <RefereeAssignmentModal
                    eventId={selectedEvent.id}
                    onClose={() => setShowRefereeModal(false)}
                />
            )}
        </div>
    );
}
