package com.example.backend.match.service.impl;

import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.BracketType;
import com.example.backend.enums.EventFormat;
import com.example.backend.enums.EventStatus;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;
import com.example.backend.event.dto.PoolStandingDTO;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.event.entity.PoolEntry;
import com.example.backend.event.entity.Team;
import com.example.backend.event.repository.EventRepository;
import com.example.backend.event.repository.PoolRepository;
import com.example.backend.exception.ValidationException;
import com.example.backend.match.dto.ForfeitRequest;
import com.example.backend.match.dto.MatchDTO;
import com.example.backend.match.dto.ScoreRulesDTO;
import com.example.backend.match.dto.ScoreUpdateRequest;
import com.example.backend.match.entity.BracketSlotSource;
import com.example.backend.match.entity.Match;
import com.example.backend.match.mapper.MatchMapper;
import com.example.backend.match.repository.BracketSlotSourceRepository;
import com.example.backend.match.repository.MatchRepository;
import com.example.backend.match.service.MatchService;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.utils.StandingsCalculator;
import com.example.backend.validation.ScoreRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final PoolRepository poolRepository;
    private final EventRepository eventRepository;
    private final BracketSlotSourceRepository bracketSlotSourceRepository;
    private final MatchMapper matchMapper;

    @Override
    @Transactional
    public MatchDTO updateScore(UUID matchId, ScoreUpdateRequest request, String username) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        verifyOwnership(match.getEvent(), username);

        if (request.getTeam1Score() == null || request.getTeam2Score() == null) {
            throw new ValidationException("Both scores are required");
        }
        ScoreRules.validate(match, request.getTeam1Score(), request.getTeam2Score());

        match.setTeam1Score(request.getTeam1Score());
        match.setTeam2Score(request.getTeam2Score());
        match.setStatus(MatchStatus.COMPLETED);
        match.setWinner(request.getTeam1Score() > request.getTeam2Score() ? match.getTeam1() : match.getTeam2());

        matchRepository.saveAndFlush(match);
        advanceTournamentState(match);

        return matchMapper.toDto(match);
    }

    @Override
    @Transactional
    public MatchDTO updateRules(UUID matchId, ScoreRulesDTO request, String username) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        verifyOwnership(match.getEvent(), username);

        if (match.getStatus().isFinished()) {
            throw new ValidationException("Cannot change scoring rules after a match has a result");
        }

        if (request.getTargetScore() != null) {
            match.setTargetScore(request.getTargetScore());
        }
        if (request.getWinByTwo() != null) {
            match.setWinByTwo(request.getWinByTwo());
        }
        if (request.getScoreCap() != null) {
            match.setScoreCap(request.getScoreCap());
        }

        matchRepository.save(match);
        return matchMapper.toDto(match);
    }

    @Override
    @Transactional
    public MatchDTO recordForfeit(UUID matchId, ForfeitRequest request, String username) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        verifyOwnership(match.getEvent(), username);

        if (match.getTeam1() == null || match.getTeam2() == null) {
            throw new ValidationException("Cannot record a forfeit before both teams are set");
        }
        if (request.getStatus() != MatchStatus.FORFEIT && request.getStatus() != MatchStatus.WALKOVER) {
            throw new ValidationException("Status must be forfeit or walkover");
        }

        Team winnerTeam;
        if (request.getWinnerId() != null && request.getWinnerId().equals(match.getTeam1().getId())) {
            winnerTeam = match.getTeam1();
        } else if (request.getWinnerId() != null && request.getWinnerId().equals(match.getTeam2().getId())) {
            winnerTeam = match.getTeam2();
        } else {
            throw new ValidationException("Winner must be one of the two teams in this match");
        }

        match.setWinner(winnerTeam);
        match.setStatus(request.getStatus());
        match.setTeam1Score(null);
        match.setTeam2Score(null);

        matchRepository.saveAndFlush(match);
        advanceTournamentState(match);

        return matchMapper.toDto(match);
    }

    private void advanceTournamentState(Match match) {
        try {
            if (match.getMatchType() == MatchType.POOL) {
                checkAndAdvancePoolWinners(match.getPool());
            } else if (match.getMatchType() == MatchType.BRACKET) {
                advanceInBracket(match);
            }

            // Check and update event lifecycle status
            updateEventStatus(match.getEvent());
            eventRepository.save(match.getEvent());

        } catch (Exception e) {
            // Log error but don't fail the score update
            System.err.println("Error advancing tournament state: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void verifyOwnership(Event event, String username) {
        Tournament tournament = event.getTournament();
        if (tournament.getOwner() == null || !tournament.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to update this match");
        }
    }

    private void checkAndAdvancePoolWinners(Pool pool) {
        List<Match> poolMatches = matchRepository.findByPoolId(pool.getId());

        boolean allComplete = poolMatches.stream()
                .allMatch(m -> m.getStatus().isFinished());

        if (!allComplete)
            return;

        pool.setComplete(true);
        poolRepository.save(pool);

        List<Team> teams = teamsInPool(pool);
        List<PoolStandingDTO> standings = StandingsCalculator.compute(teams, poolMatches);

        Event event = pool.getEvent();
        int advancementPerPool = event.getAdvancementPerPool();
        for (int rank = 1; rank <= advancementPerPool && rank <= standings.size(); rank++) {
            seedBracketSlot(pool, rank, resolveTeam(teams, standings.get(rank - 1).getTeamId()));
        }

        if (event.getWildcardCount() > 0
                && poolRepository.findByEventId(event.getId()).stream().allMatch(Pool::isComplete)) {
            resolveWildcardSlots(event);
        }
    }

    // Ranks every pool's non-advancing finishers globally to fill the WILDCARD-typed bracket
    // slots BracketGenerator pre-created. Only called once the last pool completes, since which
    // pools' runners-up qualify isn't knowable until every pool has finished.
    private void resolveWildcardSlots(Event event) {
        record Candidate(Team team, PoolStandingDTO standing) {
        }

        int advancementPerPool = event.getAdvancementPerPool();
        List<Candidate> candidates = new ArrayList<>();
        for (Pool otherPool : poolRepository.findByEventId(event.getId())) {
            List<Team> teams = teamsInPool(otherPool);
            List<PoolStandingDTO> standings = StandingsCalculator.compute(teams, matchRepository.findByPoolId(otherPool.getId()));
            for (int i = advancementPerPool; i < standings.size(); i++) {
                candidates.add(new Candidate(resolveTeam(teams, standings.get(i).getTeamId()), standings.get(i)));
            }
        }

        // ponytail: cross-pool ranking skips head-to-head since pools rarely share opponents -
        // wins, then point differential, then points-for, same as StandingsCalculator's other tiers.
        candidates.sort(Comparator
                .comparingInt((Candidate c) -> -c.standing().getWins())
                .thenComparingInt(c -> -c.standing().getPointDifferential())
                .thenComparingInt(c -> -c.standing().getPointsFor()));

        int wildcardCount = event.getWildcardCount();
        for (int rank = 1; rank <= wildcardCount && rank <= candidates.size(); rank++) {
            seedWildcardSlot(event, rank, candidates.get(rank - 1).team());
        }
    }

    private void seedWildcardSlot(Event event, int wildcardRank, Team seedTeam) {
        List<BracketSlotSource> sources = bracketSlotSourceRepository
                .findByBracketMatch_Event_IdAndWildcardRank(event.getId(), wildcardRank);
        for (BracketSlotSource source : sources) {
            Match match = source.getBracketMatch();
            if (source.getSlot() == BracketSlot.TEAM1) {
                match.setTeam1(seedTeam);
            } else {
                match.setTeam2(seedTeam);
            }
            matchRepository.save(match);
        }
    }

    private List<Team> teamsInPool(Pool pool) {
        return pool.getPoolEntries().stream().map(PoolEntry::getTeam).toList();
    }

    private Team resolveTeam(List<Team> teams, UUID teamId) {
        return teams.stream().filter(t -> t.getId().equals(teamId)).findFirst().orElseThrow();
    }

    private void seedBracketSlot(Pool pool, int sourceRank, Team seedTeam) {
        List<BracketSlotSource> sources = bracketSlotSourceRepository
                .findBySourcePoolIdAndSourceRank(pool.getId(), sourceRank);
        for (BracketSlotSource source : sources) {
            Match match = source.getBracketMatch();
            if (source.getSlot() == BracketSlot.TEAM1) {
                match.setTeam1(seedTeam);
            } else {
                match.setTeam2(seedTeam);
            }
            matchRepository.save(match);
        }
    }

    private void advanceInBracket(Match match) {
        if (match.getWinner() == null)
            return;

        if (match.getBracketType() == BracketType.FINAL && match.getBracketRound() == 1) {
            // Grand-final game 1 has no winnerNextMatch/loserNextMatch - it either decides the
            // event outright (winners' champion sweeps) or forces game 2 (reset).
            resolveGrandFinalGame1(match);
            return;
        }

        route(match.getWinnerNextMatch(), match.getWinnerNextSlot(), match.getWinner(), true);
        route(match.getLoserNextMatch(), match.getLoserNextSlot(), computeLoser(match), false);
    }

    // team1 in the grand final is always the winners'-bracket champion (BracketGenerator wires
    // winnerNextSlot=TEAM1 for the WB final, TEAM2 for the LB final) - if that side also wins
    // game 1, the losers' finalist is eliminated on their 2nd loss and no reset is needed.
    private void resolveGrandFinalGame1(Match game1) {
        Match game2 = matchRepository
                .findByEventIdAndBracketTypeAndBracketRound(game1.getEvent().getId(), BracketType.FINAL, 2)
                .orElseThrow();

        if (game1.getWinner().getId().equals(game1.getTeam1().getId())) {
            game2.setStatus(MatchStatus.SKIPPED);
        } else {
            game2.setTeam1(game1.getTeam1());
            game2.setTeam2(game1.getTeam2());
        }
        matchRepository.save(game2);
    }

    private Team computeLoser(Match match) {
        return match.getTeam1().getId().equals(match.getWinner().getId())
                ? match.getTeam2()
                : match.getTeam1();
    }

    private void route(Match targetMatch, BracketSlot slot, Team team, boolean checkForBye) {
        if (targetMatch == null || slot == null || team == null)
            return;

        if (slot == BracketSlot.TEAM1) {
            targetMatch.setTeam1(team);
        } else {
            targetMatch.setTeam2(team);
        }
        matchRepository.save(targetMatch);

        if (!checkForBye)
            return;

        // Structural bye: if no other match's winner edge also targets this match, there's no
        // real opponent coming - auto-complete now and cascade.
        List<Match> winnerSources = matchRepository.findByWinnerNextMatch_Id(targetMatch.getId());
        if (winnerSources.size() == 1) {
            targetMatch.setTeam1Score(0);
            targetMatch.setTeam2Score(0);
            targetMatch.setWinner(team);
            targetMatch.setStatus(MatchStatus.COMPLETED);
            matchRepository.save(targetMatch);
            advanceInBracket(targetMatch);
        }
    }

    private void updateEventStatus(Event event) {
        // 1. Check for transition from POOL_PLAY to ELIMINATION
        if (event.getStatus() == EventStatus.POOL_PLAY) {
            boolean allPoolsComplete = poolRepository.findByEventId(event.getId()).stream()
                    .allMatch(Pool::isComplete);

            if (allPoolsComplete) {
                event.setStatus(event.getFormat() == EventFormat.ROUND_ROBIN_ONLY
                        ? EventStatus.COMPLETED
                        : EventStatus.ELIMINATION);
            }
        }

        // 2. Check for transition to COMPLETED
        if (event.getStatus() == EventStatus.ELIMINATION) {
            List<Match> eliminationMatches = matchRepository.findByEventId(event.getId()).stream()
                    .filter(m -> m.getMatchType() == MatchType.BRACKET)
                    .toList();

            // Exclude the 3rd place match (optional) from the completion check: the terminal
            // match (nothing to advance to) that is a loser-edge target, same structural rule
            // used to identify it for display.
            Set<UUID> loserEdgeTargetIds = eliminationMatches.stream()
                    .map(Match::getLoserNextMatch)
                    .filter(m -> m != null)
                    .map(Match::getId)
                    .collect(Collectors.toSet());

            boolean allComplete = eliminationMatches.stream()
                    .filter(m -> !(m.getWinnerNextMatch() == null && loserEdgeTargetIds.contains(m.getId())))
                    .allMatch(m -> m.getStatus().isFinished());

            if (allComplete && !eliminationMatches.isEmpty()) {
                event.setStatus(EventStatus.COMPLETED);
            }
        }
    }
}
