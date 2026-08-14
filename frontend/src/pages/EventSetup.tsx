import { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { createEvent } from '../api';
import { EventFormat, ScoreRules } from '../api/types';
import { ChevronLeft, Plus, Trash2, Users } from 'lucide-react';
import './EventSetup.css';

export function EventSetup() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const tournamentId = searchParams.get('tournamentId');

    const [eventName, setEventName] = useState('');
    const [format, setFormat] = useState<EventFormat>('POOL_TO_ELIM');
    const [advancementPerPool, setAdvancementPerPool] = useState(2);
    const [wildcardCount, setWildcardCount] = useState(0);

    const [poolRules, setPoolRules] = useState<ScoreRules>({ targetScore: 11, winByTwo: true, scoreCap: 15 });
    const [playoffRules, setPlayoffRules] = useState<ScoreRules>({ targetScore: 15, winByTwo: true, scoreCap: 21 });

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

        if (!tournamentId) {
            setError('No tournament selected — go back and add an event from a tournament page');
            return;
        }

        // Validation
        if (!eventName.trim()) {
            setError('Please enter an event name');
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

        if (!Number.isInteger(advancementPerPool) || advancementPerPool < 1 || advancementPerPool > 100) {
            setError('Teams advancing per pool must be a whole number between 1 and 100');
            return;
        }

        if (!Number.isInteger(wildcardCount) || wildcardCount < 0 || wildcardCount > 100) {
            setError('Wildcard slots must be a whole number between 0 and 100');
            return;
        }

        setLoading(true);

        try {
            const response = await createEvent({
                name: eventName,
                tournamentId,
                format,
                pools: pools.map(pool => ({
                    name: pool.name,
                    teamNames: pool.teams.filter(t => t.trim())
                })),
                advancementPerPool,
                wildcardCount,
                poolStageRules: poolRules,
                ...(format !== 'ROUND_ROBIN_ONLY' ? { playoffStageRules: playoffRules } : {}),
            });

            if (response.success) {
                navigate(`/tournaments/${tournamentId}`);
            } else {
                setError(response.error || 'Failed to create event');
            }
        } catch (err) {
            setError('An unexpected error occurred');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleBack = () => {
        if (tournamentId) {
            navigate(`/tournaments/${tournamentId}`);
        } else {
            navigate('/tournaments');
        }
    };

    if (!tournamentId) {
        return (
            <div className="setup-container">
                <header className="setup-header">
                    <button onClick={() => navigate('/tournaments')} className="back-button">
                        <ChevronLeft size={20} />
                        Back
                    </button>
                    <h1>No Tournament Selected</h1>
                </header>
                <div className="setup-content">
                    <p>An event must be created from inside a tournament. <Link to="/tournaments">Go to Tournaments</Link> and use "Add Event" from a tournament page.</p>
                </div>
            </div>
        );
    }

    return (
        <div className="setup-container">
            <header className="setup-header">
                <button onClick={handleBack} className="back-button">
                    <ChevronLeft size={20} />
                    Back
                </button>
                <h1>Create New Event</h1>
            </header>

            <div className="setup-content">
                <form onSubmit={handleSubmit} className="setup-form">
                    <div className="form-section">
                        <div className="section-header">
                            <h2>Event Details</h2>
                        </div>
                        <div className="input-group">
                            <label htmlFor="name">Event Name</label>
                            <input
                                type="text"
                                id="name"
                                value={eventName}
                                onChange={(e) => setEventName(e.target.value)}
                                placeholder="e.g. Summer Pickleball Open 2024"
                                disabled={loading}
                            />
                        </div>

                        <div className="input-group">
                            <label htmlFor="format">Format</label>
                            <select
                                id="format"
                                value={format}
                                onChange={(e) => setFormat(e.target.value as EventFormat)}
                                disabled={loading}
                            >
                                <option value="POOL_TO_ELIM">Pool Play + Playoffs</option>
                                <option value="ROUND_ROBIN_ONLY">Round Robin Only</option>
                                <option value="POOL_TO_SERIES_AB">Pool Play + Series A/B</option>
                                <option value="POOL_TO_DOUBLE_ELIM">Pool Play + Double Elimination</option>
                            </select>
                        </div>

                        <div className="input-group">
                            <label htmlFor="advancementPerPool">Teams Advancing Per Pool</label>
                            <input
                                type="number"
                                id="advancementPerPool"
                                min="1"
                                value={advancementPerPool}
                                onChange={(e) => setAdvancementPerPool(parseInt(e.target.value) || 1)}
                                disabled={loading}
                            />
                        </div>

                        <div className="input-group">
                            <label htmlFor="wildcardCount">Wildcard Slots</label>
                            <input
                                type="number"
                                id="wildcardCount"
                                min="0"
                                value={wildcardCount}
                                onChange={(e) => setWildcardCount(parseInt(e.target.value) || 0)}
                                disabled={loading}
                            />
                        </div>
                    </div>

                    <div className="form-section">
                        <div className="section-header">
                            <h2>Scoring Rules</h2>
                        </div>
                        <div className="pools-grid">
                            <ScoreRulesGroup title="Pool Play" rules={poolRules} onChange={setPoolRules} disabled={loading} />
                            {format !== 'ROUND_ROBIN_ONLY' && (
                                <ScoreRulesGroup title="Playoffs" rules={playoffRules} onChange={setPlayoffRules} disabled={loading} />
                            )}
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
                            {loading ? 'Creating...' : 'Create Event'}
                        </button>
                    </div>
                </form>

                <div className="setup-info">
                    <div className="info-card">
                        <Users size={24} />
                        <h3>Builder Guide</h3>
                        <p>
                            Construct your event by adding pools and teams.
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

function ScoreRulesGroup({ title, rules, onChange, disabled }: {
    title: string;
    rules: ScoreRules;
    onChange: (rules: ScoreRules) => void;
    disabled: boolean;
}) {
    return (
        <div className="form-section pool-section">
            <div className="pool-header">
                <h3>{title}</h3>
            </div>
            <div className="teams-list">
                <div className="team-input-row">
                    <span className="team-number">Target</span>
                    <input
                        type="number"
                        min="1"
                        value={rules.targetScore}
                        onChange={(e) => onChange({ ...rules, targetScore: parseInt(e.target.value) || 0 })}
                        disabled={disabled}
                    />
                </div>
                <div className="team-input-row">
                    <span className="team-number">Cap</span>
                    <input
                        type="number"
                        min="1"
                        value={rules.scoreCap}
                        onChange={(e) => onChange({ ...rules, scoreCap: parseInt(e.target.value) || 0 })}
                        disabled={disabled}
                    />
                </div>
                <label className="team-input-row">
                    <input
                        type="checkbox"
                        checked={rules.winByTwo}
                        onChange={(e) => onChange({ ...rules, winByTwo: e.target.checked })}
                        disabled={disabled}
                    />
                    <span>Win by two</span>
                </label>
            </div>
        </div>
    );
}
