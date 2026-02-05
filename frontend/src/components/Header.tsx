import { Link, useLocation } from 'react-router-dom';
import { Trophy, Settings, Eye, Plus } from 'lucide-react';
import './Header.css';

interface HeaderProps {
    tournamentName?: string;
    isAdmin?: boolean;
}

export function Header({ tournamentName, isAdmin }: HeaderProps) {
    const location = useLocation();
    const isViewerPage = location.pathname.startsWith('/view');

    return (
        <header className="app-header">
            <div className="header-left">
                <Link to="/" className="logo">
                    <Trophy size={28} />
                    <span className="logo-text">Pickleball<span className="logo-accent">Pro</span></span>
                </Link>

                {tournamentName && (
                    <div className="tournament-badge">
                        {tournamentName}
                    </div>
                )}
            </div>

            <div className="header-right">
                {isViewerPage && (
                    <div className="viewer-badge">
                        <Eye size={14} />
                        Viewer Mode
                    </div>
                )}

                {isAdmin && (
                    <div className="admin-badge">
                        <Settings size={14} />
                        Admin
                    </div>
                )}

                <nav className="header-nav">
                    {!isViewerPage && (
                        <>
                            <Link to="/setup" className="nav-link">
                                <Plus size={16} />
                                New
                            </Link>
                            <Link to="/admin" className="nav-link">
                                <Settings size={16} />
                                Dashboard
                            </Link>
                        </>
                    )}
                </nav>
            </div>
        </header>
    );
}
