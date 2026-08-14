import { ReactNode } from 'react';
import './EmptyState.css';

interface EmptyStateProps {
    icon?: ReactNode;
    title?: string;
    description?: string;
    action?: ReactNode;
    className?: string;
}

export function EmptyState({ icon, title, description, action, className }: EmptyStateProps) {
    const classNames = className ? `empty-state ${className}` : 'empty-state';
    return (
        <div className={classNames}>
            {icon && <div className="empty-state__icon">{icon}</div>}
            {title && <h2 className="empty-state__title">{title}</h2>}
            {description && <p className="empty-state__description">{description}</p>}
            {action}
        </div>
    );
}