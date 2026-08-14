package com.example.backend.utils;

import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.BracketType;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.match.dto.ScoreRulesDTO;
import com.example.backend.match.entity.Match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Full double elimination: same Round-1 cross-seed as BracketGenerator, a winners bracket with no
 * 3rd-place match (2 losses eliminate here, not 1), a losers bracket that a team drops into on
 * its first loss, and a 2-game grand final (game 2 pre-built but skipped unless the losers'
 * finalist forces a reset by beating the winners' champion in game 1).
 *
 * Only supports a power-of-two Round-1 winners match count (no bracket byes) - same class of
 * scope cut BracketGenerator already makes for odd advancement/pool/wildcard combos.
 */
public final class DoubleElimBracketGenerator {

    private DoubleElimBracketGenerator() {
    }

    public static BracketGenerator.Result generate(Event event, List<Pool> pools, ScoreRulesDTO rules) {
        BracketGenerator.Round1 round1 = BracketGenerator.buildRound1(event, pools, rules);
        if (round1 == null) {
            return new BracketGenerator.Result(new ArrayList<>(), new ArrayList<>());
        }

        int m = round1.matches().size();
        if (m < 2 || (m & (m - 1)) != 0) {
            // ponytail: the losers-bracket drop-in math below assumes a clean power-of-two
            // Round-1 (no byes anywhere) - not built for anything else yet.
            throw new IllegalStateException("double elimination needs a power-of-two Round-1 match count, got "
                    + m + ", which isn't supported yet");
        }
        int k = Integer.numberOfTrailingZeros(m);

        List<Match> wbMatches = new ArrayList<>(round1.matches());
        wbMatches.addAll(BracketGenerator.completeSingleElim(event, round1.matches(), BracketType.WINNERS, rules, false));

        Map<Integer, List<Match>> wbByRound = wbMatches.stream()
                .collect(Collectors.groupingBy(Match::getBracketRound));
        wbByRound.values().forEach(list -> list.sort(Comparator.comparing(Match::getBracketPosition)));

        List<Match> lbMatches = new ArrayList<>();
        int lbRound = 1;
        List<Match> survivors = newRound(event, BracketType.LOSERS, lbRound, m / 2, rules);
        wireLoserEdges(wbByRound.get(1), survivors);
        lbMatches.addAll(survivors);

        for (int w = 2; w <= k + 1; w++) {
            List<Match> wbLosers = wbByRound.get(w);
            lbRound++;
            List<Match> dropIn = newRound(event, BracketType.LOSERS, lbRound, survivors.size(), rules);
            for (int i = 0; i < survivors.size(); i++) {
                survivors.get(i).setWinnerNextMatch(dropIn.get(i));
                survivors.get(i).setWinnerNextSlot(BracketSlot.TEAM1);
                wbLosers.get(i).setLoserNextMatch(dropIn.get(i));
                wbLosers.get(i).setLoserNextSlot(BracketSlot.TEAM2);
            }
            lbMatches.addAll(dropIn);
            survivors = dropIn;

            if (survivors.size() > 1) {
                lbRound++;
                List<Match> consolidated = newRound(event, BracketType.LOSERS, lbRound, survivors.size() / 2, rules);
                wireWinnerEdges(survivors, consolidated);
                lbMatches.addAll(consolidated);
                survivors = consolidated;
            }
        }

        Match wbFinal = wbByRound.get(k + 1).get(0);
        Match lbFinal = survivors.get(0);

        Match game1 = BracketGenerator.newBracketMatch(event, BracketType.FINAL, 1, 1, rules);
        Match game2 = BracketGenerator.newBracketMatch(event, BracketType.FINAL, 2, 1, rules);
        wbFinal.setWinnerNextMatch(game1);
        wbFinal.setWinnerNextSlot(BracketSlot.TEAM1);
        lbFinal.setWinnerNextMatch(game1);
        lbFinal.setWinnerNextSlot(BracketSlot.TEAM2);

        List<Match> allMatches = new ArrayList<>(wbMatches);
        allMatches.addAll(lbMatches);
        allMatches.add(game1);
        allMatches.add(game2);

        return new BracketGenerator.Result(allMatches, round1.slotSources());
    }

    private static List<Match> newRound(Event event, BracketType type, int round, int count, ScoreRulesDTO rules) {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            matches.add(BracketGenerator.newBracketMatch(event, type, round, i + 1, rules));
        }
        return matches;
    }

    private static void wireLoserEdges(List<Match> sources, List<Match> targets) {
        for (int i = 0; i < sources.size(); i++) {
            Match target = targets.get(i / 2);
            sources.get(i).setLoserNextMatch(target);
            sources.get(i).setLoserNextSlot(i % 2 == 0 ? BracketSlot.TEAM1 : BracketSlot.TEAM2);
        }
    }

    private static void wireWinnerEdges(List<Match> sources, List<Match> targets) {
        for (int i = 0; i < sources.size(); i++) {
            Match target = targets.get(i / 2);
            sources.get(i).setWinnerNextMatch(target);
            sources.get(i).setWinnerNextSlot(i % 2 == 0 ? BracketSlot.TEAM1 : BracketSlot.TEAM2);
        }
    }
}
