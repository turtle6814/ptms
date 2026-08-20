import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Settings, Eye, LogIn, LogOut, User, Calendar, Users } from 'lucide-react';
import { useAuth } from '../../context/useAuth';
import './Header.css';

export function PickleballMark() {
    return (
        <svg className="logo-mark" width="26" height="26" viewBox="0 0 40 40" aria-hidden="true">
            <circle cx="20" cy="20" r="19" fill="var(--accent-court)" />
            <ellipse cx="20" cy="9.5" rx="4.4" ry="2.8" fill="var(--bg-secondary)" transform="rotate(12 20 9.5)" />
            <ellipse cx="30.5" cy="15" rx="4" ry="2.6" fill="var(--bg-secondary)" transform="rotate(70 30.5 15)" />
            <ellipse cx="30.5" cy="26" rx="4" ry="2.6" fill="var(--bg-secondary)" transform="rotate(115 30.5 26)" />
            <ellipse cx="20" cy="31" rx="4.4" ry="2.8" fill="var(--bg-secondary)" transform="rotate(-8 20 31)" />
            <ellipse cx="9.5" cy="25" rx="4" ry="2.6" fill="var(--bg-secondary)" transform="rotate(-60 9.5 25)" />
            <ellipse cx="9.5" cy="14" rx="4" ry="2.6" fill="var(--bg-secondary)" transform="rotate(-115 9.5 14)" />
            <circle cx="20" cy="20" r="4.6" fill="var(--bg-secondary)" />
        </svg>
    );
}

export function Header() {
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
        <>
        <header className="app-header">
            <div className="header-left">
                {isViewerPage ? (
                    <div className="logo">
                        <PickleballMark />
                        <span className="logo-text">HaPi<span className="logo-accent">Pickleball</span></span>
                    </div>
                ) : (
                    <Link to="/" className="logo">
                        <PickleballMark />
                        <span className="logo-text">HaPi<span className="logo-accent">Pickleball</span></span>
                    </Link>
                )}
            </div>

            <div className="header-right">
                {isViewerPage && (
                    <div className="viewer-badge">
                        <Eye size={14} />
                        Viewer Mode
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
                                    <Link to="/tournaments" className="nav-link nav-link-events">
                                        <Calendar size={16} />
                                        Tournaments
                                    </Link>
                                    <Link to="/admin" className="nav-link nav-link-dashboard">
                                        <Settings size={16} />
                                        Dashboard
                                    </Link>
                                    {user?.role === 'ADMIN' && (
                                        <Link to="/admin/users" className="nav-link">
                                            <Users size={16} />
                                            Users
                                        </Link>
                                    )}
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
        <div className="kitchen-line" role="presentation" />
        </>
    );
}
