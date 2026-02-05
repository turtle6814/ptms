import { PoolStanding } from '../api/types';
import './PoolStandings.css';

interface PoolStandingsProps {
    poolName: string;
    standings: PoolStanding[];
    highlightTop?: number;
    showQualifyBadge?: boolean;
}

export function PoolStandings({ poolName, standings, highlightTop = 2, showQualifyBadge = true }: PoolStandingsProps) {
    return (
        <div className="pool-standings">
            <h3 className="pool-title">{poolName}</h3>
            <table className="standings-table">
                <thead>
                    <tr>
                        <th className="rank-col">#</th>
                        <th className="team-col">Team</th>
                        <th className="record-col">W-L</th>
                        <th className="pts-col">PF</th>
                        <th className="pts-col">PA</th>
                        <th className="diff-col">+/-</th>
                    </tr>
                </thead>
                <tbody>
                    {standings.map((standing, index) => (
                        <tr
                            key={standing.teamId}
                            className={index < highlightTop ? 'qualified' : ''}
                        >
                            <td className="rank-col">{index + 1}</td>
                            <td className="team-col">
                                {standing.teamName}
                                {showQualifyBadge && index < highlightTop && (
                                    <span className="qualify-badge">✓</span>
                                )}
                            </td>
                            <td className="record-col">{standing.wins}-{standing.losses}</td>
                            <td className="pts-col">{standing.pointsFor}</td>
                            <td className="pts-col">{standing.pointsAgainst}</td>
                            <td className={`diff-col ${standing.pointDifferential > 0 ? 'positive' : standing.pointDifferential < 0 ? 'negative' : ''}`}>
                                {standing.pointDifferential > 0 ? '+' : ''}{standing.pointDifferential}
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
