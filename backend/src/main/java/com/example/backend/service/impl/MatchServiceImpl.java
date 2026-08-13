package com.example.backend.service.impl;

import com.example.backend.dto.ForfeitRequest;
import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreRulesDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import com.example.backend.entity.*;
import com.example.backend.enums.BracketSlot;
import com.example.backend.enums.EventFormat;
import com.example.backend.enums.EventStatus;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;
import com.example.backend.exception.ValidationException;
import com.example.backend.repository.*;
import com.example.backend.service.MatchService;
import com.example.backend.validation.ScoreRules;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final PoolStandingRepository poolStandingRepository;
    private final PoolRepository poolRepository;
    private final EventRepository eventRepository;
    private final BracketSlotSourceRepository bracketSlotSourceRepository;
    private final ModelMapper modelMapper;

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

        return modelMapper.map(match, MatchDTO.class);
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
        return modelMapper.map(match, MatchDTO.class);
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

        return modelMapper.map(match, MatchDTO.class);
    }

    private void advanceTournamentState(Match match) {
        try {
            if (match.getMatchType() == MatchType.POOL) {
                updatePoolStandings(match);
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

    private void updatePoolStandings(Match match) {
        Pool pool = match.getPool();
        // Recalculate ALL standings for this pool from scratch to ensure mathematical
        // correctness and avoid incremental drift.

        List<Match> poolMatches = matchRepository.findByPoolId(pool.getId());
        List<PoolStanding> standings = poolStandingRepository.findByPoolId(pool.getId());

        for (PoolStanding standing : standings) {
            standing.setWins(0);
            standing.setLosses(0);
            standing.setPointsFor(0);
            standing.setPointsAgainst(0);
            standing.setPointDifferential(0);
        }

        for (Match m : poolMatches) {
            if (m.getStatus().isFinished() && m.getTeam1() != null && m.getTeam2() != null) {
                if (m.getStatus() == MatchStatus.FORFEIT || m.getStatus() == MatchStatus.WALKOVER) {
                    if (m.getWinner() != null) {
                        UUID loserId = m.getWinner().getId().equals(m.getTeam1().getId())
                                ? m.getTeam2().getId()
                                : m.getTeam1().getId();
                        updateStandingWinLossOnly(standings, m.getWinner().getId(), true);
                        updateStandingWinLossOnly(standings, loserId, false);
                    }
                } else {
                    int s1 = m.getTeam1Score() != null ? m.getTeam1Score() : 0;
                    int s2 = m.getTeam2Score() != null ? m.getTeam2Score() : 0;

                    updateStandingFromScratch(standings, m.getTeam1().getId(), s1, s2);
                    updateStandingFromScratch(standings, m.getTeam2().getId(), s2, s1);
                }
            }
        }

        poolStandingRepository.saveAll(standings);
    }

    private void updateStandingFromScratch(List<PoolStanding> standings, UUID teamId, int scored, int allowed) {
        PoolStanding s = standings.stream()
                .filter(ps -> ps.getTeam().getId().equals(teamId))
                .findFirst().orElse(null);

        if (s != null) {
            s.setPointsFor(s.getPointsFor() + scored);
            s.setPointsAgainst(s.getPointsAgainst() + allowed);
            s.setPointDifferential(s.getPointsFor() - s.getPointsAgainst());

            if (scored > allowed) {
                s.setWins(s.getWins() + 1);
            } else if (scored < allowed) {
                s.setLosses(s.getLosses() + 1);
            }
        }
    }

    private void updateStandingWinLossOnly(List<PoolStanding> standings, UUID teamId, boolean won) {
        PoolStanding s = standings.stream()
                .filter(ps -> ps.getTeam().getId().equals(teamId))
                .findFirst().orElse(null);

        if (s != null) {
            if (won) {
                s.setWins(s.getWins() + 1);
            } else {
                s.setLosses(s.getLosses() + 1);
            }
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

        List<PoolStanding> standings = poolStandingRepository.findByPoolId(pool.getId());

        standings.sort((s1, s2) -> {
            if (s2.getWins() != s1.getWins())
                return s2.getWins() - s1.getWins();
            if (s2.getPointDifferential() != s1.getPointDifferential())
                return s2.getPointDifferential() - s1.getPointDifferential();
            return s2.getPointsFor() - s1.getPointsFor();
        });

        if (standings.size() < 1)
            return;

        seedBracketSlot(pool, 1, standings.get(0).getTeam());
        if (standings.size() > 1) {
            seedBracketSlot(pool, 2, standings.get(1).getTeam());
        }
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

        route(match.getWinnerNextMatch(), match.getWinnerNextSlot(), match.getWinner(), true);
        route(match.getLoserNextMatch(), match.getLoserNextSlot(), computeLoser(match), false);
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
