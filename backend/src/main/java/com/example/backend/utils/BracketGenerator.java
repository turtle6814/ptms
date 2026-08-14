package com.example.backend.utils;

import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.match.dto.ScoreRulesDTO;
import com.example.backend.match.entity.BracketSlotSource;
import com.example.backend.match.entity.Match;
import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;
import com.example.backend.enums.SourceType;

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
        int advancementPerPool = event.getAdvancementPerPool();
        int wildcardCount = event.getWildcardCount();
        int totalQualifiers = numPools * advancementPerPool + wildcardCount;
        if (numPools < 1 || totalQualifiers < 2) {
            return new Result(allMatches, slotSources);
        }
        if (advancementPerPool % 2 != 0 && numPools % 2 != 0) {
            // ponytail: an odd advancement count with an odd pool count leaves one qualifier with
            // no Round-1 opponent, which needs a genuine round-1 bye slot - not built yet. Use an
            // even pool count or an even advancementPerPool until that's added.
            throw new IllegalStateException("advancementPerPool=" + advancementPerPool
                    + " with an odd pool count (" + numPools + ") needs a round-1 bye, which isn't supported yet");
        }
        if (wildcardCount % 2 != 0) {
            // ponytail: same round-1-bye gap as above, for an odd number of wildcards.
            throw new IllegalStateException("wildcardCount=" + wildcardCount
                    + " is odd, which needs a round-1 bye, which isn't supported yet");
        }

        List<Pool> sortedPools = new ArrayList<>(pools);
        sortedPools.sort(Comparator.comparing(Pool::getName));

        List<Match> currentRound = new ArrayList<>();
        int roundNumber = 1;
        int position = 0;

        // Tier-pair layers: rank r plays rank (advancementPerPool + 1 - r) across neighboring
        // pools - the generalized form of the original rank-1-vs-rank-2 cross-seed formula (pool
        // p's own slot gets its own top rank, the previous pool's slot gets its bottom rank).
        for (int r = 1; r * 2 <= advancementPerPool; r++) {
            int topRank = r;
            int bottomRank = advancementPerPool + 1 - r;
            for (int poolIndex = 0; poolIndex < numPools; poolIndex++) {
                Match match = newBracketMatch(event, 1, ++position, rules);
                currentRound.add(match);
                slotSources.add(newSlotSource(match, BracketSlot.TEAM1, sortedPools.get(poolIndex), topRank));
                int neighborIndex = (poolIndex + 1) % numPools;
                slotSources.add(newSlotSource(match, BracketSlot.TEAM2, sortedPools.get(neighborIndex), bottomRank));
            }
        }
        // Middle rank when advancementPerPool is odd: paired straight across consecutive pools
        // (the check above guarantees numPools is even here, so nobody is left over).
        if (advancementPerPool % 2 != 0) {
            int middleRank = advancementPerPool / 2 + 1;
            for (int poolIndex = 0; poolIndex < numPools; poolIndex += 2) {
                Match match = newBracketMatch(event, 1, ++position, rules);
                currentRound.add(match);
                slotSources.add(newSlotSource(match, BracketSlot.TEAM1, sortedPools.get(poolIndex), middleRank));
                slotSources.add(newSlotSource(match, BracketSlot.TEAM2, sortedPools.get(poolIndex + 1), middleRank));
            }
        }
        // Wildcards have no "own pool" to cross-seed against, so they simply pair off against
        // each other (rank 1 vs rank 2, etc.) in extra Round-1 matches.
        for (int i = 0; i < wildcardCount; i += 2) {
            Match match = newBracketMatch(event, 1, ++position, rules);
            currentRound.add(match);
            slotSources.add(newWildcardSlotSource(match, BracketSlot.TEAM1, i + 1));
            slotSources.add(newWildcardSlotSource(match, BracketSlot.TEAM2, i + 2));
        }

        allMatches.addAll(currentRound);

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
                int matchPosition = i + 1;
                Match target = nextRound.get((matchPosition - 1) / 2);
                match.setWinnerNextMatch(target);
                match.setWinnerNextSlot(matchPosition % 2 != 0 ? BracketSlot.TEAM1 : BracketSlot.TEAM2);
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

    private static BracketSlotSource newWildcardSlotSource(Match bracketMatch, BracketSlot slot, int wildcardRank) {
        BracketSlotSource source = new BracketSlotSource();
        source.setBracketMatch(bracketMatch);
        source.setSlot(slot);
        source.setSourceType(SourceType.WILDCARD);
        source.setWildcardRank(wildcardRank);
        // source_rank stays NOT NULL in the DB even for wildcard rows (V10 only dropped
        // source_pool_id's constraint) - reuse the wildcard rank so the column is satisfied.
        source.setSourceRank(wildcardRank);
        return source;
    }
}
