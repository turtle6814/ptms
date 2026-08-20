import { useState } from 'react';
import './TournamentTabs.css';

interface TournamentTabsProps {
    hasPoolPlay: boolean;
    hasPlayoffs: boolean;
    children: {
        poolPlay: React.ReactNode;
        playoffs: React.ReactNode;
    };
}

export function TournamentTabs({ hasPoolPlay, hasPlayoffs, children }: TournamentTabsProps) {
    const [activeTab, setActiveTab] = useState<'pool' | 'playoffs'>(hasPoolPlay ? 'pool' : 'playoffs');

    // If only one section exists, don't show tabs
    if (!hasPoolPlay && !hasPlayoffs) return null;
    if (hasPoolPlay && !hasPlayoffs) return <>{children.poolPlay}</>;
    if (!hasPoolPlay && hasPlayoffs) return <>{children.playoffs}</>;

    return (
        <div className="tournament-tabs">
            <div className="tabs-header">
                <button
                    className={`tab-btn ${activeTab === 'pool' ? 'active' : ''}`}
                    onClick={() => setActiveTab('pool')}
                >
                    🏓 Pool Play
                </button>
                <button
                    className={`tab-btn ${activeTab === 'playoffs' ? 'active' : ''}`}
                    onClick={() => setActiveTab('playoffs')}
                >
                    🏆 Playoffs
                </button>
            </div>

            <div className="tab-content">
                {activeTab === 'pool' && children.poolPlay}
                {activeTab === 'playoffs' && children.playoffs}
            </div>
        </div>
    );
}
