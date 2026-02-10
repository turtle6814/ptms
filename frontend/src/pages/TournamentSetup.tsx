import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { createTournament } from '../api';
import { ChevronLeft, Plus, Trash2, Users } from 'lucide-react';
import './TournamentSetup.css';

export function TournamentSetup() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const eventId = searchParams.get('eventId'); // Get eventId from URL if present

    const [tournamentName, setTournamentName] = useState('');

    // Initial state: 1 pool with 2 empty slots
    const [pools, setPools] = useState<{ name: string; teams: string[] }[]>([
        { name: 'Pool A', teams: ['', ''] }
    ]);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const addPool = () => {
        const nextPoolName = `Pool ${String.fromCharCode(65 + pools.length)}`;
        setPools([
            ...pools,
            { name: nextPoolName, teams: ['', ''] } // Start with 2 empty slots
        ]);
    };

    const removePool = (index: number) => {
        if (pools.length > 1) {
            const newPools = pools.filter((_, i) => i !== index);
            // Renumber pools? Optional, but nice for consistency
            const renumbered = newPools.map((pool, i) => ({
                ...pool,
                name: `Pool ${String.fromCharCode(65 + i)}`
            }));
            setPools(renumbered);
        }
    };

    const updateTeamName = (poolIndex: number, teamIndex: number, name: string) => {
        const newPools = [...pools];
        newPools[poolIndex].teams[teamIndex] = name;
        setPools(newPools);
    };

    const addTeamSlot = (poolIndex: number) => {
        const newPools = [...pools];
        newPools[poolIndex].teams.push('');
        setPools(newPools);
    };

    const removeTeamSlot = (poolIndex: number, teamIndex: number) => {
        const newPools = [...pools];
        if (newPools[poolIndex].teams.length > 2) {
            newPools[poolIndex].teams.splice(teamIndex, 1);
            setPools(newPools);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        // Validation
        if (!tournamentName.trim()) {
            setError('Please enter a tournament name');
            return;
        }

        // Validate each pool
        for (const pool of pools) {
            const validTeams = pool.teams.filter(t => t.trim());
            if (validTeams.length < 2) {
                setError(`${pool.name} needs at least 2 teams`);
                return;
            }
            if (new Set(validTeams).size !== validTeams.length) {
                setError(`Duplicate team names found in ${pool.name}`);
                return;
            }
        }

        setLoading(true);

        try {
            const response = await createTournament({
                name: tournamentName,
                eventId: eventId || undefined, // Link to event if coming from event context
                pools: pools.map(pool => ({
                    name: pool.name,
                    teamNames: pool.teams.filter(t => t.trim())
                }))
            });

            if (response.success) {
                // Navigate back to event if created from event, otherwise go to admin
                if (eventId) {
                    navigate(`/events/${eventId}`);
                } else {
                    navigate('/admin');
                }
            } else {
                setError(response.error || 'Failed to create tournament');
            }
        } catch (err) {
            setError('An unexpected error occurred');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleBack = () => {
        if (eventId) {
            navigate(`/events/${eventId}`);
        } else {
            navigate('/admin');
        }
    };

    return (
        <div className="setup-container">
            <header className="setup-header">
                <button onClick={handleBack} className="back-button">
                    <ChevronLeft size={20} />
                    Back
                </button>
                <h1>Create New Tournament</h1>
            </header>

            <div className="setup-content">
                <form onSubmit={handleSubmit} className="setup-form">
                    <div className="form-section">
                        <div className="section-header">
                            <h2>Tournament Details</h2>
                        </div>
                        <div className="input-group">
                            <label htmlFor="name">Tournament Name</label>
                            <input
                                type="text"
                                id="name"
                                value={tournamentName}
                                onChange={(e) => setTournamentName(e.target.value)}
                                placeholder="e.g. Summer Pickleball Open 2024"
                                disabled={loading}
                            />
                        </div>
                    </div>

                    <div className="pools-grid">
                        {pools.map((pool, poolIndex) => (
                            <div key={poolIndex} className="form-section pool-section">
                                <div className="pool-header">
                                    <h3>{pool.name}</h3>
                                    <div className="pool-header-actions">
                                        <span className="team-count">{pool.teams.filter(t => t.trim()).length} Teams</span>
                                        {pools.length > 1 && (
                                            <button
                                                type="button"
                                                onClick={() => removePool(poolIndex)}
                                                className="remove-pool-btn"
                                                title="Remove Pool"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        )}
                                    </div>
                                </div>

                                <div className="teams-list">
                                    {pool.teams.map((team, teamIndex) => (
                                        <div key={teamIndex} className="team-input-row">
                                            <span className="team-number">#{teamIndex + 1}</span>
                                            <input
                                                type="text"
                                                value={team}
                                                onChange={(e) => updateTeamName(poolIndex, teamIndex, e.target.value)}
                                                placeholder={`Team Name`}
                                                disabled={loading}
                                            />
                                            {pool.teams.length > 2 && (
                                                <button
                                                    type="button"
                                                    onClick={() => removeTeamSlot(poolIndex, teamIndex)}
                                                    className="remove-team-btn"
                                                    disabled={loading}
                                                >
                                                    <Trash2 size={18} />
                                                </button>
                                            )}
                                        </div>
                                    ))}
                                </div>

                                <button
                                    type="button"
                                    onClick={() => addTeamSlot(poolIndex)}
                                    className="add-team-btn"
                                    disabled={loading}
                                >
                                    <Plus size={18} />
                                    Add Team Slot
                                </button>
                            </div>
                        ))}

                        {/* Add Pool Button */}
                        <div className="add-pool-section">
                            <button
                                type="button"
                                onClick={addPool}
                                className="add-pool-btn"
                                disabled={loading}
                            >
                                <Plus size={24} />
                                <span>Add Another Pool</span>
                            </button>
                        </div>
                    </div>

                    {error && <div className="error-message">{error}</div>}

                    <div className="form-actions sticky-actions">
                        <button
                            type="submit"
                            className="create-btn"
                            disabled={loading}
                        >
                            {loading ? 'Creating...' : 'Create Tournament'}
                        </button>
                    </div>
                </form>

                <div className="setup-info">
                    <div className="info-card">
                        <Users size={24} />
                        <h3>Builder Guide</h3>
                        <p>
                            Construct your tournament by adding pools and teams.
                        </p>
                        <ul>
                            <li><strong>Pools:</strong> Add as many as needed (A, B, C...)</li>
                            <li><strong>Teams:</strong> Minimum 2 per pool.</li>
                            <li><strong>Scoring:</strong> Round-robin within pools.</li>
                            <li><strong>Playoffs:</strong> Top 2 from EACH pool advance.</li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    );
}
