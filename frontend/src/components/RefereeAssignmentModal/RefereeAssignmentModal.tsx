import { useEffect, useState } from 'react';
import { Modal } from '../Modal/Modal';
import { getEventReferees, getReferees, assignReferee, unassignReferee } from '../../api';
import { EventReferee, User } from '../../api/types';
import { X } from 'lucide-react';
import './RefereeAssignmentModal.css';

interface RefereeAssignmentModalProps {
    eventId: string;
    onClose: () => void;
}

export function RefereeAssignmentModal({ eventId, onClose }: RefereeAssignmentModalProps) {
    const [assigned, setAssigned] = useState<EventReferee[]>([]);
    const [availableReferees, setAvailableReferees] = useState<User[]>([]);
    const [selectedUserId, setSelectedUserId] = useState('');
    const [loading, setLoading] = useState(true);

    const load = async () => {
        const [assignedRes, refereesRes] = await Promise.all([
            getEventReferees(eventId),
            getReferees(),
        ]);
        if (assignedRes.success) setAssigned(assignedRes.data);
        if (refereesRes.success) setAvailableReferees(refereesRes.data);
        setLoading(false);
    };

    useEffect(() => {
        load();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [eventId]);

    const handleAssign = async () => {
        if (!selectedUserId) return;
        const response = await assignReferee(eventId, selectedUserId);
        if (response.success) {
            setSelectedUserId('');
            await load();
        }
    };

    const handleUnassign = async (userId: string) => {
        const response = await unassignReferee(eventId, userId);
        if (response.success) {
            await load();
        }
    };

    const unassignedReferees = availableReferees.filter(
        r => !assigned.some(a => a.refereeId === r.id)
    );

    return (
        <Modal onClose={onClose}>
            <button className="modal-close" onClick={onClose}>×</button>
            <h2>Event Referees</h2>

            {loading ? (
                <p>Loading...</p>
            ) : (
                <>
                    <ul className="referee-list">
                        {assigned.length === 0 && <li className="referee-list-empty">No referees assigned</li>}
                        {assigned.map(a => (
                            <li key={a.id} className="referee-list-item">
                                <span>{a.refereeUsername}</span>
                                <button onClick={() => handleUnassign(a.refereeId)} aria-label="Remove referee">
                                    <X size={14} />
                                </button>
                            </li>
                        ))}
                    </ul>

                    <div className="referee-add-row">
                        <select value={selectedUserId} onChange={e => setSelectedUserId(e.target.value)}>
                            <option value="">Select a referee...</option>
                            {unassignedReferees.map(r => (
                                <option key={r.id} value={r.id}>{r.username}</option>
                            ))}
                        </select>
                        <button onClick={handleAssign} disabled={!selectedUserId}>Add</button>
                    </div>
                </>
            )}
        </Modal>
    );
}
