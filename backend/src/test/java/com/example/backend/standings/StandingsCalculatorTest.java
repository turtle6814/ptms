package com.example.backend.standings;

import com.example.backend.dto.PoolStandingDTO;
import com.example.backend.entity.Match;
import com.example.backend.entity.Team;
import com.example.backend.enums.MatchStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingsCalculatorTest {

    private static Team team(String name) {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName(name);
        return team;
    }

    private static Match completedMatch(Team team1, Team team2, int score1, int score2) {
        Match match = new Match();
        match.setTeam1(team1);
        match.setTeam2(team2);
        match.setTeam1Score(score1);
        match.setTeam2Score(score2);
        match.setWinner(score1 > score2 ? team1 : team2);
        match.setStatus(MatchStatus.COMPLETED);
        return match;
    }

    private static PoolStandingDTO standingFor(List<PoolStandingDTO> standings, Team team) {
        return standings.stream()
                .filter(s -> s.getTeamId().equals(team.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No standing for team " + team.getName()));
    }

    @Test
    void computesWinsLossesAndPoints() {
        Team a = team("A");
        Team b = team("B");
        Team c = team("C");

        List<Match> matches = List.of(
                completedMatch(a, b, 11, 5),
                completedMatch(a, c, 11, 9),
                completedMatch(b, c, 11, 3));

        List<PoolStandingDTO> standings = StandingsCalculator.compute(List.of(a, b, c), matches);

        assertEquals(3, standings.size());
        PoolStandingDTO aStanding = standingFor(standings, a);
        assertEquals(2, aStanding.getWins());
        assertEquals(0, aStanding.getLosses());
        assertEquals(22, aStanding.getPointsFor());
        assertEquals(14, aStanding.getPointsAgainst());
        assertEquals(8, aStanding.getPointDifferential());
    }

    @Test
    void forfeitCountsWinLossOnlyNotPoints() {
        Team a = team("A");
        Team b = team("B");

        Match forfeit = new Match();
        forfeit.setTeam1(a);
        forfeit.setTeam2(b);
        forfeit.setWinner(a);
        forfeit.setStatus(MatchStatus.FORFEIT);

        List<PoolStandingDTO> standings = StandingsCalculator.compute(List.of(a, b), List.of(forfeit));

        PoolStandingDTO aStanding = standingFor(standings, a);
        PoolStandingDTO bStanding = standingFor(standings, b);
        assertEquals(1, aStanding.getWins());
        assertEquals(0, aStanding.getPointsFor());
        assertEquals(1, bStanding.getLosses());
        assertEquals(0, bStanding.getPointsFor());
    }

    @Test
    void sortsByWinsThenPointDifferentialThenPointsFor() {
        Team a = team("A");
        Team b = team("B");
        Team c = team("C");

        // a beats b 11-9 (a: 1W +2diff, b: 1L -2diff)
        // b beats c 11-2 (b: 1W +9diff, c: 1L -9diff)
        // b: 1W-1L, diff +7 total; a: 1W-0L, diff +2; c: 0W-1L, diff -9
        List<Match> matches = List.of(
                completedMatch(a, b, 11, 9),
                completedMatch(b, c, 11, 2));

        List<PoolStandingDTO> standings = StandingsCalculator.compute(List.of(a, b, c), matches);

        assertEquals(b.getId(), standings.get(0).getTeamId());
        assertEquals(a.getId(), standings.get(1).getTeamId());
        assertEquals(c.getId(), standings.get(2).getTeamId());
    }

    @Test
    void unfinishedAndIncompleteMatchesAreIgnored() {
        Team a = team("A");
        Team b = team("B");

        Match pending = new Match();
        pending.setTeam1(a);
        pending.setTeam2(b);
        pending.setStatus(MatchStatus.PENDING);

        List<PoolStandingDTO> standings = StandingsCalculator.compute(List.of(a, b), List.of(pending));

        assertEquals(0, standingFor(standings, a).getWins());
        assertEquals(0, standingFor(standings, b).getLosses());
    }
}
