package com.example.backend.utils;

import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.BracketType;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.match.dto.ScoreRulesDTO;
import com.example.backend.match.entity.Match;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test - no Spring context, no database. Verifies the consolation (LOSERS) bracket is
 * wired correctly off the round-1 losers of the reused winners bracket, with no 3rd-place match
 * and no reset final.
 */
class SeriesAbBracketGeneratorTest {

    private static final ScoreRulesDTO RULES = rules(15, true, 21);

    private static ScoreRulesDTO rules(int target, boolean winByTwo, int cap) {
        ScoreRulesDTO dto = new ScoreRulesDTO();
        dto.setTargetScore(target);
        dto.setWinByTwo(winByTwo);
        dto.setScoreCap(cap);
        return dto;
    }

    private static Pool pool(String name) {
        Pool pool = new Pool();
        pool.setId(UUID.randomUUID());
        pool.setName(name);
        return pool;
    }

    private static Match match(List<Match> matches, BracketType type, int round, int position) {
        return matches.stream()
                .filter(m -> m.getBracketType() == type && m.getBracketRound() == round
                        && m.getBracketPosition() == position)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + type + " match at round " + round + " position " + position));
    }

    @Test
    void twoPoolsProduceOneConsolationMatchAsBChampion() {
        Event event = new Event();
        List<Pool> pools = List.of(pool("Pool A"), pool("Pool B"));

        BracketGenerator.Result result = SeriesAbBracketGenerator.generate(event, pools, RULES);

        Match wR1p1 = match(result.matches(), BracketType.WINNERS, 1, 1);
        Match wR1p2 = match(result.matches(), BracketType.WINNERS, 1, 2);
        Match bFinal = match(result.matches(), BracketType.LOSERS, 1, 1);

        // 3 winners matches (round 1 + final) + 1 consolation match. The winners 3rd-place
        // match is dropped: round 1 IS the semifinal round here, so its losers all feed the
        // B bracket and a 3rd-place match would have no incoming edges (never completes).
        assertEquals(4, result.matches().size());
        assertTrue(result.matches().stream()
                .noneMatch(m -> m.getBracketType() == BracketType.WINNERS
                        && m.getBracketRound() == 2 && m.getBracketPosition() == 2));

        assertEquals(bFinal, wR1p1.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM1, wR1p1.getLoserNextSlot());
        assertEquals(bFinal, wR1p2.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM2, wR1p2.getLoserNextSlot());

        assertNull(bFinal.getWinnerNextMatch());
        assertNull(bFinal.getLoserNextMatch()); // no 3rd-place match in the B bracket
    }

    @Test
    void fourPoolsGiveConsolationBracketItsOwnRounds() {
        Event event = new Event();
        List<Pool> pools = List.of(pool("Pool A"), pool("Pool B"), pool("Pool C"), pool("Pool D"));

        BracketGenerator.Result result = SeriesAbBracketGenerator.generate(event, pools, RULES);

        Match wR1p1 = match(result.matches(), BracketType.WINNERS, 1, 1);
        Match wR1p2 = match(result.matches(), BracketType.WINNERS, 1, 2);
        Match wR1p3 = match(result.matches(), BracketType.WINNERS, 1, 3);
        Match wR1p4 = match(result.matches(), BracketType.WINNERS, 1, 4);
        Match bR1p1 = match(result.matches(), BracketType.LOSERS, 1, 1);
        Match bR1p2 = match(result.matches(), BracketType.LOSERS, 1, 2);
        Match bFinal = match(result.matches(), BracketType.LOSERS, 2, 1);

        assertEquals(11, result.matches().size()); // 8 winners matches + 3 consolation matches

        assertEquals(bR1p1, wR1p1.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM1, wR1p1.getLoserNextSlot());
        assertEquals(bR1p1, wR1p2.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM2, wR1p2.getLoserNextSlot());
        assertEquals(bR1p2, wR1p3.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM1, wR1p3.getLoserNextSlot());
        assertEquals(bR1p2, wR1p4.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM2, wR1p4.getLoserNextSlot());

        assertEquals(bFinal, bR1p1.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM1, bR1p1.getWinnerNextSlot());
        assertEquals(bFinal, bR1p2.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM2, bR1p2.getWinnerNextSlot());
        assertNull(bFinal.getWinnerNextMatch());
        assertNull(bFinal.getLoserNextMatch());

        // The winners bracket keeps its own 3rd-place match, untouched by consolation wiring.
        Match wFinal = match(result.matches(), BracketType.WINNERS, 3, 1);
        Match wThirdPlace = match(result.matches(), BracketType.WINNERS, 3, 2);
        assertNull(wFinal.getLoserNextMatch());
        assertEquals(BracketType.WINNERS, wThirdPlace.getBracketType());
    }

    @Test
    void oddRound1CountIsRejected() {
        Event event = new Event();
        List<Pool> pools = List.of(pool("Pool A"), pool("Pool B"), pool("Pool C"));

        assertThrows(IllegalStateException.class, () -> SeriesAbBracketGenerator.generate(event, pools, RULES));
    }
}
