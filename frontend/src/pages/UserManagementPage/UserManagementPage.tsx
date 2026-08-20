import { useEffect, useState } from 'react';
import { Users } from 'lucide-react';
import { Header } from '../../components/Header/Header';
import { LoadingState } from '../../components/LoadingState/LoadingState';
import { getAllUsers, updateUserRole } from '../../api';
import { User, Role } from '../../api/types';
import { useAuth } from '../../context/useAuth';
import './UserManagementPage.css';

const ROLES: Role[] = ['ADMIN', 'ORGANIZER', 'REFEREE', 'USER'];

export function UserManagementPage() {
    const { user: currentUser } = useAuth();
    const [users, setUsers] = useState<User[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const init = async () => {
            const response = await getAllUsers();
            if (response.success) {
                setUsers(response.data);
            }
            setIsLoading(false);
        };
        init();
    }, []);

    const handleRoleChange = async (userId: string, role: Role) => {
        const response = await updateUserRole(userId, role);
        if (response.success) {
            setUsers(prev => prev.map(u => u.id === userId ? response.data : u));
        }
    };

    if (isLoading) {
        return (
            <div className="events-page">
                <Header />
                <LoadingState message="Loading users..." />
            </div>
        );
    }

    return (
        <div className="events-page">
            <Header />
            <main className="events-content">
                <div className="events-header">
                    <div className="header-text">
                        <h1>Users</h1>
                        <p>Manage account roles</p>
                    </div>
                </div>

                <section className="events-section">
                    <h2 className="section-title">
                        <Users size={20} />
                        All Users
                    </h2>
                    <table className="user-table">
                        <thead>
                            <tr>
                                <th>Username</th>
                                <th>Phone</th>
                                <th>Role</th>
                            </tr>
                        </thead>
                        <tbody>
                            {users.map(user => (
                                <tr key={user.id}>
                                    <td>{user.username}</td>
                                    <td>{user.phoneNumber}</td>
                                    <td>
                                        <select
                                            value={user.role}
                                            disabled={user.id === currentUser?.id}
                                            onChange={e => handleRoleChange(user.id, e.target.value as Role)}
                                        >
                                            {ROLES.map(role => (
                                                <option key={role} value={role}>{role}</option>
                                            ))}
                                        </select>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </section>
            </main>
        </div>
    );
}
