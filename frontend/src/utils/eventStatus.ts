import { Event } from '../api/types';

export function getStatusLabel(status: Event['status']): string {
    switch (status) {
        case 'SETUP':
            return 'Setup';
        case 'POOL_PLAY':
            return 'Pool Play';
        case 'ELIMINATION':
            return 'Playoffs';
        case 'COMPLETED':
            return 'Complete';
        default:
            return status;
    }
}

export function getStatusColor(status: Event['status']): string {
    switch (status) {
        case 'POOL_PLAY':
            return 'status-pool';
        case 'ELIMINATION':
            return 'status-elimination';
        case 'COMPLETED':
            return 'status-completed';
        default:
            return 'status-setup';
    }
}