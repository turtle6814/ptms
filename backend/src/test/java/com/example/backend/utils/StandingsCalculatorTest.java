package com.example.backend.utils;

import com.example.backend.event.dto.response.PoolStandingResponse;
import com.example.backend.event.entity.Team;
import com.example.backend.match.entity.Match;
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

    private static PoolStandingResponse standingFor(List<PoolStandingResponse> standings, Team team) {
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

        List<PoolStandingResponse> standings = StandingsCalculator.compute(List.of(a, b, c), matches);

        assertEquals(3, standings.size());
        PoolStandingResponse aStanding = standingFor(standings, a);
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

        List<PoolStandingResponse> standings = StandingsCalculator.compute(List.of(a, b), List.of(forfeit));

        PoolStandingResponse aStanding = standingFor(standings, a);
        PoolStandingResponse bStanding = standingFor(standings, b);
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

        // a and b never play each other, so this stays a pure point-differential tiebreak
        // (head-to-head is checked first but has nothing to compare for this pair). Both beat
        // c instead, by different margins.
        // a beats c 11-9 (a: 1W +2diff)
        // b beats c 11-2 (b: 1W +9diff)
        // c: 0W-2L, diff -11 total
        List<Match> matches = List.of(
                completedMatch(a, c, 11, 9),
                completedMatch(b, c, 11, 2));

        List<PoolStandingResponse> standings = StandingsCalculator.compute(List.of(a, b, c), matches);

        assertEquals(b.getId(), standings.get(0).getTeamId());
        assertEquals(a.getId(), standings.get(1).getTeamId());
        assertEquals(c.getId(), standings.get(2).getTeamId());
    }

    @Test
    void headToHeadBreaksWinsTieBeforePointDifferential() {
        Team a = team("A");
        Team b = team("B");
        Team c = team("C");

        // a beats b directly, but b has the better point differential overall - head-to-head
        // must still put a first.
        // a beats b 11-9 (a: 1W +2diff, b: 1L -2diff)
        // b beats c 11-2 (b: 1W +9diff total, c: 0W -9diff)
        List<Match> matches = List.of(
                completedMatch(a, b, 11, 9),
                completedMatch(b, c, 11, 2));

        List<PoolStandingResponse> standings = StandingsCalculator.compute(List.of(a, b, c), matches);

        assertEquals(a.getId(), standings.get(0).getTeamId());
        assertEquals(b.getId(), standings.get(1).getTeamId());
        assertEquals(c.getId(), standings.get(2).getTeamId());
    }

    @Test
    void sortsByPointsForWhenWinsAndDifferentialTie() {
        Team a = team("A");
        Team b = team("B");
        Team c = team("C");
        Team d = team("D");
        Team e = team("E");
        Team f = team("F");

        // a: beats c 15-5, loses to d 5-15 -> 1W-1L, diff 0, pointsFor 20
        // b: beats e 11-1, loses to f 1-11 -> 1W-1L, diff 0, pointsFor 12
        // c/d/e/f only ever play against a or b, never each other, so their
        // own records don't interact with a's or b's.
        List<Match> matches = List.of(
                completedMatch(a, c, 15, 5),
                completedMatch(d, a, 15, 5),
                completedMatch(b, e, 11, 1),
                completedMatch(f, b, 11, 1));

        List<PoolStandingResponse> standings = StandingsCalculator.compute(List.of(a, b, c, d, e, f), matches);

        PoolStandingResponse aStanding = standingFor(standings, a);
        PoolStandingResponse bStanding = standingFor(standings, b);
        assertEquals(1, aStanding.getWins());
        assertEquals(0, aStanding.getPointDifferential());
        assertEquals(20, aStanding.getPointsFor());
        assertEquals(1, bStanding.getWins());
        assertEquals(0, bStanding.getPointDifferential());
        assertEquals(12, bStanding.getPointsFor());

        int aIndex = standings.indexOf(aStanding);
        int bIndex = standings.indexOf(bStanding);
        assertEquals(true, aIndex < bIndex);
    }

    @Test
    void unfinishedAndIncompleteMatchesAreIgnored() {
        Team a = team("A");
        Team b = team("B");

        Match pending = new Match();
        pending.setTeam1(a);
        pending.setTeam2(b);
        pending.setStatus(MatchStatus.PENDING);

        List<PoolStandingResponse> standings = StandingsCalculator.compute(List.of(a, b), List.of(pending));

        assertEquals(0, standingFor(standings, a).getWins());
        assertEquals(0, standingFor(standings, b).getLosses());
    }
}
