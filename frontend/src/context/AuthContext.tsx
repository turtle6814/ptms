import { useState, useEffect, ReactNode } from 'react';
import { User } from '../api/types';
import * as api from '../api';
import { AuthContext, AuthContextType } from './useAuth';

// ================================
// Auth Provider Component
// ================================

interface AuthProviderProps {
    children: ReactNode;
}

export function AuthProvider({ children }: AuthProviderProps) {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);

    // Initialize auth state from API on mount
    useEffect(() => {
        async function initAuth() {
            const token = localStorage.getItem('pickleball_auth_token');
            if (token) {
                try {
                    const response = await api.getCurrentUser();
                    if (response.success && response.data) {
                        setUser(response.data);
                    } else {
                        // Token might be invalid
                        localStorage.removeItem('pickleball_auth_token');
                    }
                } catch (error) {
                    console.error('Failed to initialize auth:', error);
                    localStorage.removeItem('pickleball_auth_token');
                }
            }
            setIsLoading(false);
        }
        initAuth();
    }, []);

    const login = async (phoneNumber: string, password: string) => {
        const response = await api.login({ phoneNumber, password });
        if (response.success && response.data) {
            setUser(response.data.user);
            localStorage.setItem('pickleball_auth_token', response.data.token);
            return { success: true };
        }
        return { success: false, error: response.error };
    };

    const signup = async (username: string, phoneNumber: string, password: string) => {
        const response = await api.signup({ username, phoneNumber, password });
        if (response.success && response.data) {
            setUser(response.data.user);
            localStorage.setItem('pickleball_auth_token', response.data.token);
            return { success: true };
        }
        return { success: false, error: response.error };
    };

    const logout = async () => {
        await api.logout();
        localStorage.removeItem('pickleball_auth_token');
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
