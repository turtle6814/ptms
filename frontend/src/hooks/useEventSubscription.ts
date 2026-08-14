import { useEffect, useRef } from 'react';
import { Event } from '../api/types';
import { subscribeEvent } from '../api';

export function useEventSubscription(
    selectedEvent: Event | null,
    onUpdate: (updated: Event) => void
): void {
    const eventId = selectedEvent?.id;
    const onUpdateRef = useRef(onUpdate);

    useEffect(() => {
        onUpdateRef.current = onUpdate;
    });

    useEffect(() => {
        if (!eventId) return;
        const unsubscribe = subscribeEvent(eventId, (updated) => onUpdateRef.current(updated));
        return unsubscribe;
    }, [eventId]);
}