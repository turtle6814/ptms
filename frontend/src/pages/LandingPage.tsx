import { Link } from 'react-router-dom';
import { Users, BarChart3, Share2, ArrowRight, Zap, Shield, Globe } from 'lucide-react';
import { Header, PickleballMark } from '../components/Header';
import { useAuth } from '../context/useAuth';
import { Navigate } from 'react-router-dom';
import { getHomeRoute } from '../utils/roleHome';
import './LandingPage.css';

export function LandingPage() {
    const { isAuthenticated, isLoading, user } = useAuth();

    // Redirect to the role's dashboard if already logged in (USER has none, stays here)
    if (!isLoading && isAuthenticated && user) {
        const home = getHomeRoute(user.role);
        if (home !== '/') {
            return <Navigate to={home} replace />;
        }
    }

    return (
        <div className="landing-page">
            <Header />

            {/* Hero Section */}
            <section className="hero-section">
                <div className="hero-content">
                    <p className="hero-eyebrow">Pools &middot; brackets &middot; live scoring</p>
                    <h1>
                        Run the whole tournament <em>from the sideline.</em>
                    </h1>
                    <p className="hero-description">
                        Seed pools, generate the bracket, and update scores court-side.
                        Standings and the elimination bracket update for every spectator
                        the moment you hit save.
                    </p>
                    <div className="hero-actions">
                        <Link to="/signup" className="btn-primary">
                            Get Started Free
                            <ArrowRight size={18} />
                        </Link>
                        <Link to="/login" className="btn-secondary">
                            Sign In
                        </Link>
                    </div>
                </div>
                <div className="hero-visual">
                    <div className="hero-scoreboard slat">
                        <span className="hero-scoreboard-tag">
                            <span className="hero-scoreboard-dot" />
                            LIVE &middot; POOL A
                        </span>
                        <span className="hero-scoreboard-score">4&nbsp;&middot;&nbsp;2</span>
                        <span className="hero-scoreboard-teams">Nguyen / Tran vs. Alvarez / Reyes</span>
                    </div>
                </div>
            </section>

            {/* Features Section */}
            <section className="features-section">
                <h2>Everything You Need</h2>
                <div className="features-grid">
                    <div className="feature-card">
                        <div className="feature-icon">
                            <Users size={24} />
                        </div>
                        <h3>Pool Play</h3>
                        <p>Organize teams into pools with automatic match generation and standings calculation.</p>
                    </div>
                    <div className="feature-card">
                        <div className="feature-icon">
                            <BarChart3 size={24} />
                        </div>
                        <h3>Live Scoring</h3>
                        <p>Update scores in real-time. Standings and brackets update automatically.</p>
                    </div>
                    <div className="feature-card">
                        <div className="feature-icon">
                            <Share2 size={24} />
                        </div>
                        <h3>Spectator View</h3>
                        <p>Share a QR code so spectators can follow along on their own devices.</p>
                    </div>
                    <div className="feature-card">
                        <div className="feature-icon">
                            <Shield size={24} />
                        </div>
                        <h3>Secure Access</h3>
                        <p>Admin-only controls ensure only you can update scores and manage tournaments.</p>
                    </div>
                    <div className="feature-card">
                        <div className="feature-icon">
                            <Globe size={24} />
                        </div>
                        <h3>Works Everywhere</h3>
                        <p>Responsive design works on phones, tablets, and desktops.</p>
                    </div>
                    <div className="feature-card">
                        <div className="feature-icon">
                            <Zap size={24} />
                        </div>
                        <h3>Lightning Fast</h3>
                        <p>Instant updates with no page reloads. Built for speed.</p>
                    </div>
                </div>
            </section>

            {/* CTA Section */}
            <section className="cta-section">
                <div className="cta-content">
                    <h2>Ready to Run Your Tournament?</h2>
                    <p>Join tournament organizers who trust HaPiPickleball.</p>
                    <Link to="/signup" className="btn-primary btn-large">
                        Create Your Free Account
                        <ArrowRight size={20} />
                    </Link>
                </div>
            </section>

            {/* Footer */}
            <footer className="landing-footer">
                <div className="footer-content">
                    <div className="footer-brand">
                        <PickleballMark />
                        <span>HaPi<span className="accent">Pickleball</span></span>
                    </div>
                    <p>© 2026 HaPiPickleball · Built with ❤️ by sdtb.tm14</p>
                </div>
            </footer>
        </div>
    );
}
