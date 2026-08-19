import { Role } from '../api/types';

export function getHomeRoute(role: Role): string {
    switch (role) {
        case 'ADMIN':
        case 'ORGANIZER':
            return '/admin';
        case 'REFEREE':
            return '/referee';
        default:
            return '/';
    }
}
