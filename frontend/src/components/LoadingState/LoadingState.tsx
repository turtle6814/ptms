import { RefreshCw } from 'lucide-react';
import './LoadingState.css';

interface LoadingStateProps {
    message?: string;
}

export function LoadingState({ message = 'Loading...' }: LoadingStateProps) {
    return (
        <div className="loading-state">
            <RefreshCw className="loading-state__spinner" size={32} />
            <p>{message}</p>
        </div>
    );
}