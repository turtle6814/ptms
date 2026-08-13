package com.example.backend.utils;

import com.example.backend.dto.PoolStandingDTO;
import com.example.backend.entity.Match;
import com.example.backend.entity.Team;
import com.example.backend.enums.MatchStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Computes pool standings directly from matches - no database access, no stored state.
 * Replaces the pool_standings table (ERD.md P6): the table was fully recomputed from
 * scratch on every score update anyway, so it bought nothing but drift risk.
 */
public final class StandingsCalculator {

    private StandingsCalculator() {
    }

    public static List<PoolStandingDTO> compute(List<Team> teams, List<Match> poolMatches) {
        Map<UUID, PoolStandingDTO> standingsByTeamId = new LinkedHashMap<>();
        for (Team team : teams) {
            PoolStandingDTO standing = new PoolStandingDTO();
            standing.setTeamId(team.getId());
            standing.setTeamName(team.getName());
            standingsByTeamId.put(team.getId(), standing);
        }

        for (Match match : poolMatches) {
            if (!match.getStatus().isFinished() || match.getTeam1() == null || match.getTeam2() == null) {
                continue;
            }

            if (match.getStatus() == MatchStatus.FORFEIT || match.getStatus() == MatchStatus.WALKOVER) {
                if (match.getWinner() != null) {
                    UUID winnerId = match.getWinner().getId();
                    UUID loserId = winnerId.equals(match.getTeam1().getId())
                            ? match.getTeam2().getId()
                            : match.getTeam1().getId();
                    addWinLoss(standingsByTeamId, winnerId, true);
                    addWinLoss(standingsByTeamId, loserId, false);
                }
            } else {
                int team1Score = match.getTeam1Score() != null ? match.getTeam1Score() : 0;
                int team2Score = match.getTeam2Score() != null ? match.getTeam2Score() : 0;
                addResult(standingsByTeamId, match.getTeam1().getId(), team1Score, team2Score);
                addResult(standingsByTeamId, match.getTeam2().getId(), team2Score, team1Score);
            }
        }

        List<PoolStandingDTO> standings = new ArrayList<>(standingsByTeamId.values());
        standings.sort((s1, s2) -> {
            if (s2.getWins() != s1.getWins())
                return s2.getWins() - s1.getWins();
            if (s2.getPointDifferential() != s1.getPointDifferential())
                return s2.getPointDifferential() - s1.getPointDifferential();
            return s2.getPointsFor() - s1.getPointsFor();
        });
        return standings;
    }

    private static void addWinLoss(Map<UUID, PoolStandingDTO> standingsByTeamId, UUID teamId, boolean won) {
        PoolStandingDTO standing = standingsByTeamId.get(teamId);
        if (standing == null) {
            return;
        }
        if (won) {
            standing.setWins(standing.getWins() + 1);
        } else {
            standing.setLosses(standing.getLosses() + 1);
        }
    }

    private static void addResult(Map<UUID, PoolStandingDTO> standingsByTeamId, UUID teamId, int scored, int allowed) {
        PoolStandingDTO standing = standingsByTeamId.get(teamId);
        if (standing == null) {
            return;
        }
        standing.setPointsFor(standing.getPointsFor() + scored);
        standing.setPointsAgainst(standing.getPointsAgainst() + allowed);
        standing.setPointDifferential(standing.getPointsFor() - standing.getPointsAgainst());
        if (scored > allowed) {
            standing.setWins(standing.getWins() + 1);
        } else if (scored < allowed) {
            standing.setLosses(standing.getLosses() + 1);
        }
    }
}
