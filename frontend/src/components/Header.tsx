import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Trophy, Settings, Eye, Plus, LogIn, LogOut, User, Calendar } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import './Header.css';

interface HeaderProps {
    tournamentName?: string;
    isAdmin?: boolean;
}

export function Header({ tournamentName, isAdmin }: HeaderProps) {
    const location = useLocation();
    const navigate = useNavigate();
    const { user, isAuthenticated, logout } = useAuth();
    const isViewerPage = location.pathname.startsWith('/view');
    const isAuthPage = location.pathname === '/login' || location.pathname === '/signup';

    const handleLogout = async () => {
        await logout();
        navigate('/login');
    };

    return (
        <header className="app-header">
            <div className="header-left">
                {isViewerPage ? (
                    <div className="logo">
                        <Trophy size={28} />
                        <span className="logo-text">Pickleball<span className="logo-accent">Pro</span></span>
                    </div>
                ) : (
                    <Link to="/" className="logo">
                        <Trophy size={28} />
                        <span className="logo-text">Pickleball<span className="logo-accent">Pro</span></span>
                    </Link>
                )}

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

                {/* Show user info when authenticated - but NOT on viewer pages */}
                {!isViewerPage && isAuthenticated && user && (
                    <div className="user-section">
                        <div className="user-badge">
                            <User size={14} />
                            <span className="username">{user.username}</span>
                        </div>
                        <button onClick={handleLogout} className="logout-button">
                            <LogOut size={16} />
                            Logout
                        </button>
                    </div>
                )}

                <nav className="header-nav">
                    {!isViewerPage && !isAuthPage && (
                        <>
                            {isAuthenticated ? (
                                <>
                                    {/* <Link to="/setup" className="nav-link">
                                        <Plus size={16} />
                                        New
                                    </Link> */}
                                    <Link to="/events" className="nav-link nav-link-events">
                                        <Calendar size={16} />
                                        Events
                                    </Link>
                                    <Link to="/admin" className="nav-link nav-link-dashboard">
                                        <Settings size={16} />
                                        Dashboard
                                    </Link>
                                </>
                            ) : (
                                <Link to="/login" className="nav-link nav-link-primary">
                                    <LogIn size={16} />
                                    Login
                                </Link>
                            )}
                        </>
                    )}
                </nav>
            </div>
        </header>
    );
}
