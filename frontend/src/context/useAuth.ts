import { createContext, useContext } from 'react';
import { Role, User } from '../api/types';

// ================================
// Auth Context Types
// ================================

export interface AuthContextType {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    login: (phoneNumber: string, password: string) => Promise<{ success: boolean; error?: string; role?: Role }>;
    signup: (username: string, phoneNumber: string, password: string) => Promise<{ success: boolean; error?: string }>;
    logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextType | null>(null);

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
