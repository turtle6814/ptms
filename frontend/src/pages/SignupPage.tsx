import { useState, FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { UserPlus, User, Phone, Lock, AlertCircle, Loader2, CheckCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { Header } from '../components/Header';
import './SignupPage.css';

export function SignupPage() {
    const [username, setUsername] = useState('');
    const [phoneNumber, setPhoneNumber] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);

    const { signup } = useAuth();
    const navigate = useNavigate();

    const validateForm = (): string | null => {
        if (!username.trim() || !phoneNumber.trim() || !password.trim() || !confirmPassword.trim()) {
            return 'Please fill in all fields';
        }
        if (username.length < 3) {
            return 'Username must be at least 3 characters';
        }
        if (!/^[0-9+\-\s()]+$/.test(phoneNumber) || phoneNumber.replace(/\D/g, '').length < 10) {
            return 'Please enter a valid phone number (at least 10 digits)';
        }
        if (password.length < 6) {
            return 'Password must be at least 6 characters';
        }
        if (password !== confirmPassword) {
            return 'Passwords do not match';
        }
        return null;
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        setError(null);

        const validationError = validateForm();
        if (validationError) {
            setError(validationError);
            return;
        }

        setIsLoading(true);
        try {
            const result = await signup(username, phoneNumber, password);
            if (result.success) {
                navigate('/admin');
            } else {
                setError(result.error || 'Signup failed');
            }
        } catch {
            setError('An unexpected error occurred');
        } finally {
            setIsLoading(false);
        }
    };

    const isPasswordMatch = password.length > 0 && confirmPassword.length > 0 && password === confirmPassword;

    return (
        <div className="signup-page">
            <Header />
            <main className="signup-container">
                <div className="signup-card">
                    <div className="signup-header">
                        <div className="signup-icon">
                            <UserPlus size={32} />
                        </div>
                        <h1>Create Account</h1>
                        <p>Join to manage your pickleball tournaments</p>
                    </div>

                    <form onSubmit={handleSubmit} className="signup-form">
                        {error && (
                            <div className="error-message">
                                <AlertCircle size={16} />
                                <span>{error}</span>
                            </div>
                        )}

                        <div className="form-group">
                            <label htmlFor="username">
                                <User size={16} />
                                Username
                            </label>
                            <input
                                type="text"
                                id="username"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                placeholder="Choose a username"
                                autoComplete="username"
                                disabled={isLoading}
                            />
                        </div>

                        <div className="form-group">
                            <label htmlFor="phoneNumber">
                                <Phone size={16} />
                                Phone Number
                            </label>
                            <input
                                type="tel"
                                id="phoneNumber"
                                value={phoneNumber}
                                onChange={(e) => setPhoneNumber(e.target.value)}
                                placeholder="Enter your phone number"
                                autoComplete="tel"
                                disabled={isLoading}
                            />
                        </div>

                        <div className="form-group">
                            <label htmlFor="password">
                                <Lock size={16} />
                                Password
                            </label>
                            <input
                                type="password"
                                id="password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                placeholder="Create a password"
                                autoComplete="new-password"
                                disabled={isLoading}
                            />
                        </div>

                        <div className="form-group">
                            <label htmlFor="confirmPassword">
                                <Lock size={16} />
                                Confirm Password
                                {isPasswordMatch && (
                                    <CheckCircle size={14} className="password-match-icon" />
                                )}
                            </label>
                            <input
                                type="password"
                                id="confirmPassword"
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                placeholder="Confirm your password"
                                autoComplete="new-password"
                                disabled={isLoading}
                                className={isPasswordMatch ? 'input-match' : ''}
                            />
                        </div>

                        <button
                            type="submit"
                            className="signup-button"
                            disabled={isLoading}
                        >
                            {isLoading ? (
                                <>
                                    <Loader2 size={18} className="spinner" />
                                    Creating Account...
                                </>
                            ) : (
                                <>
                                    <UserPlus size={18} />
                                    Create Account
                                </>
                            )}
                        </button>
                    </form>

                    <div className="signup-footer">
                        <p>
                            Already have an account?{' '}
                            <Link to="/login">Sign in</Link>
                        </p>
                    </div>
                </div>
            </main>
        </div>
    );
}
