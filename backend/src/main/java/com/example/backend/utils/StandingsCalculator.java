package com.example.backend.utils;

import com.example.backend.event.dto.response.PoolStandingResponse;
import com.example.backend.event.entity.Team;
import com.example.backend.match.entity.Match;
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

    public static List<PoolStandingResponse> compute(List<Team> teams, List<Match> poolMatches) {
        Map<UUID, PoolStandingResponse> standingsByTeamId = new LinkedHashMap<>();
        for (Team team : teams) {
            PoolStandingResponse standing = new PoolStandingResponse();
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

        List<PoolStandingResponse> standings = new ArrayList<>(standingsByTeamId.values());
        standings.sort((s1, s2) -> {
            if (s2.getWins() != s1.getWins())
                return s2.getWins() - s1.getWins();
            int headToHead = compareHeadToHead(s1.getTeamId(), s2.getTeamId(), poolMatches);
            if (headToHead != 0)
                return headToHead;
            if (s2.getPointDifferential() != s1.getPointDifferential())
                return s2.getPointDifferential() - s1.getPointDifferential();
            if (s2.getPointsFor() != s1.getPointsFor())
                return s2.getPointsFor() - s1.getPointsFor();
            return s1.getTeamName().compareTo(s2.getTeamName());
        });
        return standings;
    }

    // Negative when team1 should rank above team2, i.e. team1 won their head-to-head match(es).
    private static int compareHeadToHead(UUID team1Id, UUID team2Id, List<Match> poolMatches) {
        int team1Wins = 0;
        int team2Wins = 0;
        for (Match match : poolMatches) {
            if (!match.getStatus().isFinished() || match.getTeam1() == null || match.getTeam2() == null) {
                continue;
            }
            UUID matchTeam1 = match.getTeam1().getId();
            UUID matchTeam2 = match.getTeam2().getId();
            boolean isBetweenThem = (matchTeam1.equals(team1Id) && matchTeam2.equals(team2Id))
                    || (matchTeam1.equals(team2Id) && matchTeam2.equals(team1Id));
            if (!isBetweenThem) {
                continue;
            }
            UUID winnerId = headToHeadWinnerId(match);
            if (team1Id.equals(winnerId)) {
                team1Wins++;
            } else if (team2Id.equals(winnerId)) {
                team2Wins++;
            }
        }
        return team2Wins - team1Wins;
    }

    private static UUID headToHeadWinnerId(Match match) {
        if (match.getStatus() == MatchStatus.FORFEIT || match.getStatus() == MatchStatus.WALKOVER) {
            return match.getWinner() != null ? match.getWinner().getId() : null;
        }
        int team1Score = match.getTeam1Score() != null ? match.getTeam1Score() : 0;
        int team2Score = match.getTeam2Score() != null ? match.getTeam2Score() : 0;
        if (team1Score == team2Score) {
            return null;
        }
        return team1Score > team2Score ? match.getTeam1().getId() : match.getTeam2().getId();
    }

    private static void addWinLoss(Map<UUID, PoolStandingResponse> standingsByTeamId, UUID teamId, boolean won) {
        PoolStandingResponse standing = standingsByTeamId.get(teamId);
        if (standing == null) {
            return;
        }
        if (won) {
            standing.setWins(standing.getWins() + 1);
        } else {
            standing.setLosses(standing.getLosses() + 1);
        }
    }

    private static void addResult(Map<UUID, PoolStandingResponse> standingsByTeamId, UUID teamId, int scored, int allowed) {
        PoolStandingResponse standing = standingsByTeamId.get(teamId);
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
