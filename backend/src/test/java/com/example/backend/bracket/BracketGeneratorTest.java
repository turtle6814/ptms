package com.example.backend.bracket;

import com.example.backend.dto.ScoreRulesDTO;
import com.example.backend.entity.BracketSlotSource;
import com.example.backend.entity.Event;
import com.example.backend.entity.Match;
import com.example.backend.entity.Pool;
import com.example.backend.enums.BracketSlot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test - no Spring context, no database. Verifies BracketGenerator's pointer wiring
 * directly, proving the ERD's claim that the generator is "fully unit-testable with no database."
 */
class BracketGeneratorTest {

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

    private static Match match(List<Match> matches, int round, int position) {
        return matches.stream()
                .filter(m -> m.getBracketRound() == round && m.getBracketPosition() == position)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No match at round " + round + " position " + position));
    }

    private static BracketSlotSource sourceFor(List<BracketSlotSource> sources, Pool pool, int rank) {
        return sources.stream()
                .filter(s -> s.getSourcePool().equals(pool) && s.getSourceRank() == rank)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No slot source for pool " + pool.getName() + " rank " + rank));
    }

    @Test
    void singlePoolProducesOneTerminalMatchSeededBothRanks() {
        Event event = new Event();
        Pool poolA = pool("Pool A");

        BracketGenerator.Result result = BracketGenerator.generate(event, List.of(poolA), RULES);

        assertEquals(1, result.matches().size());
        Match finalMatch = match(result.matches(), 1, 1);
        assertNull(finalMatch.getWinnerNextMatch());
        assertNull(finalMatch.getLoserNextMatch());

        assertEquals(2, result.bracketSlotSources().size());
        assertEquals(finalMatch, sourceFor(result.bracketSlotSources(), poolA, 1).getBracketMatch());
        assertEquals(BracketSlot.TEAM1, sourceFor(result.bracketSlotSources(), poolA, 1).getSlot());
        assertEquals(finalMatch, sourceFor(result.bracketSlotSources(), poolA, 2).getBracketMatch());
        assertEquals(BracketSlot.TEAM2, sourceFor(result.bracketSlotSources(), poolA, 2).getSlot());
    }

    @Test
    void twoPoolsWireCrossSeedingAndThirdPlace() {
        Event event = new Event();
        Pool poolA = pool("Pool A");
        Pool poolB = pool("Pool B");

        BracketGenerator.Result result = BracketGenerator.generate(event, List.of(poolA, poolB), RULES);

        assertEquals(4, result.matches().size());
        Match r1pos1 = match(result.matches(), 1, 1);
        Match r1pos2 = match(result.matches(), 1, 2);
        Match finalMatch = match(result.matches(), 2, 1);
        Match thirdPlace = match(result.matches(), 2, 2);

        assertEquals(finalMatch, r1pos1.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM1, r1pos1.getWinnerNextSlot());
        assertEquals(finalMatch, r1pos2.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM2, r1pos2.getWinnerNextSlot());
        assertNull(finalMatch.getWinnerNextMatch());

        assertEquals(thirdPlace, r1pos1.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM1, r1pos1.getLoserNextSlot());
        assertEquals(thirdPlace, r1pos2.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM2, r1pos2.getLoserNextSlot());

        // Cross-seeding: pool A's rank-2 seed goes to pool B's own Round-1 slot, and vice versa.
        assertEquals(r1pos1, sourceFor(result.bracketSlotSources(), poolA, 1).getBracketMatch());
        assertEquals(r1pos2, sourceFor(result.bracketSlotSources(), poolA, 2).getBracketMatch());
        assertEquals(r1pos2, sourceFor(result.bracketSlotSources(), poolB, 1).getBracketMatch());
        assertEquals(r1pos1, sourceFor(result.bracketSlotSources(), poolB, 2).getBracketMatch());
    }

    @Test
    void threePoolsProduceExactlyOneByeTarget() {
        Event event = new Event();
        Pool poolA = pool("Pool A");
        Pool poolB = pool("Pool B");
        Pool poolC = pool("Pool C");

        BracketGenerator.Result result = BracketGenerator.generate(event, List.of(poolA, poolB, poolC), RULES);

        assertEquals(7, result.matches().size());
        Match r1pos1 = match(result.matches(), 1, 1);
        Match r1pos2 = match(result.matches(), 1, 2);
        Match r1pos3 = match(result.matches(), 1, 3);
        Match r2pos1 = match(result.matches(), 2, 1);
        Match r2pos2 = match(result.matches(), 2, 2);
        Match finalMatch = match(result.matches(), 3, 1);
        Match thirdPlace = match(result.matches(), 3, 2);

        assertEquals(r2pos1, r1pos1.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM1, r1pos1.getWinnerNextSlot());
        assertEquals(r2pos1, r1pos2.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM2, r1pos2.getWinnerNextSlot());
        assertEquals(r2pos2, r1pos3.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM1, r1pos3.getWinnerNextSlot());

        // Structural bye: r2pos2 is targeted by exactly one Round-1 match (r1pos3) - no sibling
        // edge will ever fill its other slot.
        long matchesTargetingR2Pos2 = result.matches().stream()
                .filter(m -> r2pos2.equals(m.getWinnerNextMatch()))
                .count();
        assertEquals(1, matchesTargetingR2Pos2);
        long matchesTargetingR2Pos1 = result.matches().stream()
                .filter(m -> r2pos1.equals(m.getWinnerNextMatch()))
                .count();
        assertEquals(2, matchesTargetingR2Pos1);

        assertEquals(finalMatch, r2pos1.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM1, r2pos1.getWinnerNextSlot());
        assertEquals(finalMatch, r2pos2.getWinnerNextMatch());
        assertEquals(BracketSlot.TEAM2, r2pos2.getWinnerNextSlot());

        // 3rd place is fed by the semifinal round (round 2), not round 1.
        assertEquals(thirdPlace, r2pos1.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM1, r2pos1.getLoserNextSlot());
        assertEquals(thirdPlace, r2pos2.getLoserNextMatch());
        assertEquals(BracketSlot.TEAM2, r2pos2.getLoserNextSlot());
        assertNull(r1pos3.getLoserNextMatch());

        assertEquals(r1pos1, sourceFor(result.bracketSlotSources(), poolA, 1).getBracketMatch());
        assertEquals(r1pos3, sourceFor(result.bracketSlotSources(), poolA, 2).getBracketMatch());
        assertEquals(r1pos2, sourceFor(result.bracketSlotSources(), poolB, 1).getBracketMatch());
        assertEquals(r1pos1, sourceFor(result.bracketSlotSources(), poolB, 2).getBracketMatch());
        assertEquals(r1pos3, sourceFor(result.bracketSlotSources(), poolC, 1).getBracketMatch());
        assertEquals(r1pos2, sourceFor(result.bracketSlotSources(), poolC, 2).getBracketMatch());
    }

    @Test
    void fourPoolsHaveNoByes() {
        Event event = new Event();
        List<Pool> pools = List.of(pool("Pool A"), pool("Pool B"), pool("Pool C"), pool("Pool D"));

        BracketGenerator.Result result = BracketGenerator.generate(event, pools, RULES);

        assertEquals(8, result.matches().size());
        for (Match m : result.matches()) {
            if (m.getBracketRound() == 3) {
                continue; // final + 3rd place are terminal, nothing targets checked here
            }
            assertTrue(m.getWinnerNextMatch() != null, "every non-final match should advance somewhere");
        }

        // Every Round-2 match receives exactly two Round-1 winner edges - no byes.
        Match r2pos1 = match(result.matches(), 2, 1);
        Match r2pos2 = match(result.matches(), 2, 2);
        long intoR2pos1 = result.matches().stream().filter(m -> r2pos1.equals(m.getWinnerNextMatch())).count();
        long intoR2pos2 = result.matches().stream().filter(m -> r2pos2.equals(m.getWinnerNextMatch())).count();
        assertEquals(2, intoR2pos1);
        assertEquals(2, intoR2pos2);
    }
}
