package com.example.backend.utils;

import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.BracketType;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.match.dto.ScoreRulesDTO;
import com.example.backend.match.entity.Match;

import java.util.ArrayList;
import java.util.List;

/**
 * Series A/B: the same winners bracket as single elimination, plus a second single-elimination
 * bracket seeded only from round-1 losers of the winners bracket, deciding an independent
 * "B champion" (no reset, no shot at the A champion). This is what makes it schema-distinct from
 * full double elimination, which drops every round's losers into the losers bracket.
 */
public final class SeriesAbBracketGenerator {

    private SeriesAbBracketGenerator() {
    }

    public static BracketGenerator.Result generate(Event event, List<Pool> pools, ScoreRulesDTO rules) {
        BracketGenerator.Result winners = BracketGenerator.generate(event, pools, rules);

        List<Match> round1 = winners.matches().stream()
                .filter(m -> m.getBracketType() == BracketType.WINNERS && m.getBracketRound() == 1)
                .toList();

        if (round1.size() < 2) {
            return winners;
        }
        if (round1.size() % 2 != 0) {
            // ponytail: an odd number of round-1 winners matches leaves one round-1 loser with no
            // consolation-bracket opponent, which needs a genuine bye slot - not built yet.
            throw new IllegalStateException("round1 winners match count=" + round1.size()
                    + " is odd, which needs a consolation-bracket bye, which isn't supported yet");
        }

        // When the winners bracket has only two rounds (round-1 count == 2), its first round is
        // also the semifinal round, so every semifinal loser drops into the B bracket below. The
        // winners 3rd-place match BracketGenerator.generate() added then has no incoming loser
        // edges - it can never be filled, stays PENDING forever, and would block event
        // completion. Drop it for this topology.
        List<Match> winnersMatches = winners.matches();
        if (round1.size() == 2) {
            winnersMatches = winnersMatches.stream()
                    .filter(m -> !(m.getBracketType() == BracketType.WINNERS
                            && m.getBracketRound() == 2 && m.getBracketPosition() == 2))
                    .toList();
        }

        int consolationRound1Count = round1.size() / 2;
        List<Match> consolationRound1 = new ArrayList<>();
        for (int i = 0; i < consolationRound1Count; i++) {
            consolationRound1.add(BracketGenerator.newBracketMatch(event, BracketType.LOSERS, 1, i + 1, rules));
        }
        for (int i = 0; i < round1.size(); i++) {
            Match source = round1.get(i);
            int matchPosition = i + 1;
            Match target = consolationRound1.get((matchPosition - 1) / 2);
            source.setLoserNextMatch(target);
            source.setLoserNextSlot(matchPosition % 2 != 0 ? BracketSlot.TEAM1 : BracketSlot.TEAM2);
        }

        List<Match> allMatches = new ArrayList<>(winnersMatches);
        allMatches.addAll(consolationRound1);
        // No 3rd-place match for the B bracket - Series A/B only crowns a single B champion.
        allMatches.addAll(BracketGenerator.completeSingleElim(event, consolationRound1, BracketType.LOSERS, rules, false));

        return new BracketGenerator.Result(allMatches, winners.bracketSlotSources());
    }
}
