import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User } from '../api/types';
import * as api from '../api/mockApi';

// ================================
// Auth Context Types
// ================================

interface AuthContextType {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    login: (phoneNumber: string, password: string) => Promise<{ success: boolean; error?: string }>;
    signup: (username: string, phoneNumber: string, password: string) => Promise<{ success: boolean; error?: string }>;
    logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

// ================================
// Auth Provider Component
// ================================

interface AuthProviderProps {
    children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    // Initialize auth state from localStorage on mount
    useEffect(() => {
        async function initAuth() {
            try {
                const response = await api.getCurrentUser();
                if (response.success && response.data) {
                    setUser(response.data);
                }
            } catch (error) {
                console.error('Failed to initialize auth:', error);
            } finally {
                setIsLoading(false);
            }
        }
        initAuth();
    }, []);

    const login = async (phoneNumber: string, password: string) => {
        const response = await api.login({ phoneNumber, password });
        if (response.success && response.data) {
            setUser(response.data.user);
            return { success: true };
        }
        return { success: false, error: response.error };
    };

    const signup = async (username: string, phoneNumber: string, password: string) => {
        const response = await api.signup({ username, phoneNumber, password });
        if (response.success && response.data) {
            setUser(response.data.user);
            return { success: true };
        }
        return { success: false, error: response.error };
    };

    const logout = async () => {
        await api.logout();
        setUser(null);
    };

    const value: AuthContextType = {
        user,
        isAuthenticated: user !== null,
        isLoading,
        login,
        signup,
        logout,
    };

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
}

// ================================
// useAuth Hook
// ================================

export function useAuth(): AuthContextType {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
}
