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

/**
 * Plain unit test - no Spring context, no database. Verifies the winners/losers bracket topology
 * and grand-final wiring for power-of-two Round-1 sizes.
 */
class DoubleElimBracketGeneratorTest {

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
    void fourTeamsSingleLbRoundThenGrandFinal() {
        // k=1: 4 pools, advancementPerPool=1 -> the odd-advancementPerPool "middle rank" pairing
        // pairs pools straight across (A vs B, C vs D), giving Round-1 exactly 2 matches.
        Event event = new Event();
        event.setAdvancementPerPool(1);
        List<Pool> pools = List.of(pool("Pool A"), pool("Pool B"), pool("Pool C"), pool("Pool D"));

        BracketGenerator.Result result = DoubleElimBracketGenerator.generate(event, pools, RULES);

        Match wR1p1 = match(result.matches(), BracketType.WINNERS, 1, 1);
        Match wR1p2 = match(result.matches(), BracketType.WINNERS, 1, 2);
        Match wFinal = match(result.matches(), BracketType.WINNERS, 2, 1);
        Match lR1p1 = match(result.matches(), BracketType.LOSERS, 1, 1);
        Match lFinal = match(result.matches(), BracketType.LOSERS, 2, 1);
        Match game1 = match(result.matches(), BracketType.FINAL, 1, 1);
        Match game2 = match(result.matches(), BracketType.FINAL, 2, 1);

        assertEquals(7, result.matches().size()); // 3 WB + 2 LB + 2 grand-final games

        // WB Round-1 losers drop straight into the (only) LB round.
        assertEquals(lR1p1, wR1p1.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM1, wR1p1.getLoserNextSlot());
        assertEquals(lR1p1, wR1p2.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM2, wR1p2.getLoserNextSlot());

        // LB round 1's winner meets the WB final's loser in the LB final.
        assertEquals(lFinal, lR1p1.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM1, lR1p1.getWinnerNextSlot());
        assertEquals(lFinal, wFinal.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM2, wFinal.getLoserNextSlot());

        // Grand final: WB champion is always team1 (game 1), LB champion is always team2.
        assertEquals(game1, wFinal.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM1, wFinal.getWinnerNextSlot());
        assertEquals(game1, lFinal.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM2, lFinal.getWinnerNextSlot());
        assertNull(game1.getWinnerNextMatch());
        assertNull(game2.getTeam1());
        assertNull(game2.getTeam2());
    }

    @Test
    void eightTeamsAlternatesDropInAndConsolidationRounds() {
        // k=2: 4 pools, default advancementPerPool=2 -> tier-pair cross-seed gives Round-1
        // exactly 4 matches (same Round-1 shape as BracketGeneratorTest.fourPoolsHaveNoByes).
        Event event = new Event();
        List<Pool> pools = List.of(pool("Pool A"), pool("Pool B"), pool("Pool C"), pool("Pool D"));

        BracketGenerator.Result result = DoubleElimBracketGenerator.generate(event, pools, RULES);

        // WB: 4 + 2 + 1 = 7. LB: round1(2) + round2 drop-in(2) + round3 consolidate(1) +
        // round4 final drop-in(1) = 6. Grand final: 2. Total 15.
        assertEquals(15, result.matches().size());

        Match wR1p1 = match(result.matches(), BracketType.WINNERS, 1, 1);
        Match wR1p2 = match(result.matches(), BracketType.WINNERS, 1, 2);
        Match wR1p3 = match(result.matches(), BracketType.WINNERS, 1, 3);
        Match wR1p4 = match(result.matches(), BracketType.WINNERS, 1, 4);
        Match wSemi1 = match(result.matches(), BracketType.WINNERS, 2, 1);
        Match wSemi2 = match(result.matches(), BracketType.WINNERS, 2, 2);
        Match wFinal = match(result.matches(), BracketType.WINNERS, 3, 1);

        Match lR1p1 = match(result.matches(), BracketType.LOSERS, 1, 1);
        Match lR1p2 = match(result.matches(), BracketType.LOSERS, 1, 2);
        Match lR2p1 = match(result.matches(), BracketType.LOSERS, 2, 1); // drop-in vs WB semis
        Match lR2p2 = match(result.matches(), BracketType.LOSERS, 2, 2);
        Match lR3p1 = match(result.matches(), BracketType.LOSERS, 3, 1); // consolidation
        Match lFinal = match(result.matches(), BracketType.LOSERS, 4, 1); // drop-in vs WB final

        // WB Round-1 losers pair off into LB round 1.
        assertEquals(lR1p1, wR1p1.getLoserNextMatch());
        assertEquals(lR1p1, wR1p2.getLoserNextMatch());
        assertEquals(lR1p2, wR1p3.getLoserNextMatch());
        assertEquals(lR1p2, wR1p4.getLoserNextMatch());

        // LB round 1 winners drop-in against WB semifinal losers.
        assertEquals(lR2p1, lR1p1.getWinnerNextMatch());
        assertEquals(lR2p1, wSemi1.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM1, lR1p1.getWinnerNextSlot());
        assertEquals(BracketSlot.TEAM2, wSemi1.getLoserNextSlot());
        assertEquals(lR2p2, lR1p2.getWinnerNextMatch());
        assertEquals(lR2p2, wSemi2.getLoserNextMatch());

        // LB round 2 winners consolidate against each other.
        assertEquals(lR3p1, lR2p1.getWinnerNextMatch());
        assertEquals(lR3p1, lR2p2.getWinnerNextMatch());

        // LB round 3's winner drops in against the WB final's loser to decide the LB champion.
        assertEquals(lFinal, lR3p1.getWinnerNextMatch());
        assertEquals(lFinal, wFinal.getLoserNextMatch());

        Match game1 = match(result.matches(), BracketType.FINAL, 1, 1);
        assertEquals(game1, wFinal.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM1, wFinal.getWinnerNextSlot());
        assertEquals(game1, lFinal.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM2, lFinal.getWinnerNextSlot());
    }

    @Test
    void nonPowerOfTwoRound1IsRejected() {
        // 3 pools, default advancementPerPool=2 -> tier-pair cross-seed gives Round-1 exactly 3
        // matches (same Round-1 shape as BracketGeneratorTest.threePoolsProduceExactlyOneByeTarget)
        // - a valid single-elim bracket, but not power-of-two, so double elim rejects it.
        Event event = new Event();
        List<Pool> pools = List.of(pool("Pool A"), pool("Pool B"), pool("Pool C"));

        assertThrows(IllegalStateException.class, () -> DoubleElimBracketGenerator.generate(event, pools, RULES));
    }
}
