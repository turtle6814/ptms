package com.example.backend.bracket;

import com.example.backend.dto.ScoreRulesDTO;
import com.example.backend.entity.BracketSlotSource;
import com.example.backend.entity.Event;
import com.example.backend.entity.Match;
import com.example.backend.entity.Pool;
import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the elimination bracket's placeholder matches and pool-seeding sources as plain
 * objects, with no repository/database access, so the bracket topology (round sizes, byes,
 * winner/loser pointer wiring, cross-seeding) can be tested in isolation.
 */
public final class BracketGenerator {

    private BracketGenerator() {
    }

    public record Result(List<Match> matches, List<BracketSlotSource> bracketSlotSources) {
    }

    public static Result generate(Event event, List<Pool> pools, ScoreRulesDTO rules) {
        List<Match> allMatches = new ArrayList<>();
        List<BracketSlotSource> slotSources = new ArrayList<>();

        int numPools = pools.size();
        if (numPools < 1) {
            return new Result(allMatches, slotSources);
        }

        List<Pool> sortedPools = new ArrayList<>(pools);
        sortedPools.sort(Comparator.comparing(Pool::getName));

        List<Match> currentRound = new ArrayList<>();
        int roundNumber = 1;

        if (numPools == 1) {
            currentRound.add(newBracketMatch(event, 1, 1, rules));
        } else {
            for (int i = 0; i < numPools; i++) {
                currentRound.add(newBracketMatch(event, 1, i + 1, rules));
            }
        }
        allMatches.addAll(currentRound);

        // Seeding sources: pool i's rank-1 seed goes to its own Round-1 slot; rank-2 goes to the
        // previous pool's Round-1 slot (today's cross-seed formula, expressed as data instead of
        // runtime arithmetic). With a single pool, both ranks target that one match.
        for (int poolIndex = 0; poolIndex < numPools; poolIndex++) {
            Pool pool = sortedPools.get(poolIndex);
            Match ownSlot = currentRound.get(poolIndex);
            slotSources.add(newSlotSource(ownSlot, BracketSlot.TEAM1, pool, 1));

            if (numPools > 1) {
                int previousPoolIndex = (poolIndex - 1 + numPools) % numPools;
                Match previousPoolOwnSlot = currentRound.get(previousPoolIndex);
                slotSources.add(newSlotSource(previousPoolOwnSlot, BracketSlot.TEAM2, pool, 2));
            } else {
                slotSources.add(newSlotSource(ownSlot, BracketSlot.TEAM2, pool, 2));
            }
        }

        List<Match> semifinalRound = null;
        int matchCount = currentRound.size();

        while (matchCount > 1) {
            roundNumber++;
            int nextRoundMatchCount = (int) Math.ceil((double) matchCount / 2);

            List<Match> nextRound = new ArrayList<>();
            for (int i = 0; i < nextRoundMatchCount; i++) {
                nextRound.add(newBracketMatch(event, roundNumber, i + 1, rules));
            }
            allMatches.addAll(nextRound);

            for (int i = 0; i < currentRound.size(); i++) {
                Match match = currentRound.get(i);
                int position = i + 1;
                Match target = nextRound.get((position - 1) / 2);
                match.setWinnerNextMatch(target);
                match.setWinnerNextSlot(position % 2 != 0 ? BracketSlot.TEAM1 : BracketSlot.TEAM2);
            }

            semifinalRound = currentRound;
            matchCount = nextRoundMatchCount;
            currentRound = nextRound;
        }

        // Add 3rd Place Match if there's at least a semifinal round. The round immediately
        // before the final always has exactly 2 matches (invariant of this halving algorithm:
        // the loop only stops once a round reaches size 1, so the round before it must have had
        // ceil(n/2) == 1, i.e. n was 1 or 2 - and 1 would have already stopped the loop earlier).
        if (roundNumber >= 2) {
            Match thirdPlace = newBracketMatch(event, roundNumber, 2, rules);
            allMatches.add(thirdPlace);

            for (int i = 0; i < semifinalRound.size(); i++) {
                Match match = semifinalRound.get(i);
                match.setLoserNextMatch(thirdPlace);
                match.setLoserNextSlot(i == 0 ? BracketSlot.TEAM1 : BracketSlot.TEAM2);
            }
        }

        return new Result(allMatches, slotSources);
    }

    private static Match newBracketMatch(Event event, int round, int position, ScoreRulesDTO rules) {
        Match match = new Match();
        match.setEvent(event);
        match.setMatchType(MatchType.BRACKET);
        match.setBracketRound(round);
        match.setBracketPosition(position);
        match.setStatus(MatchStatus.PENDING);
        match.setTargetScore(rules.getTargetScore());
        match.setWinByTwo(rules.getWinByTwo());
        match.setScoreCap(rules.getScoreCap());
        return match;
    }

    private static BracketSlotSource newSlotSource(Match bracketMatch, BracketSlot slot, Pool sourcePool,
            int sourceRank) {
        BracketSlotSource source = new BracketSlotSource();
        source.setBracketMatch(bracketMatch);
        source.setSlot(slot);
        source.setSourcePool(sourcePool);
        source.setSourceRank(sourceRank);
        return source;
    }
}
