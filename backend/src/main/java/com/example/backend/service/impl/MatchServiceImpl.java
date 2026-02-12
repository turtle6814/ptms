package com.example.backend.service.impl;

import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final MatchRepository matchRepository;
    private final PoolStandingRepository poolStandingRepository;
    private final PoolRepository poolRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public MatchDTO updateScore(UUID matchId, ScoreUpdateRequest request) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        match.setTeam1Score(request.getTeam1Score());
        match.setTeam2Score(request.getTeam2Score());
        match.setStatus(Match.Status.completed);

        // Determine winner
        if (request.getTeam1Score() > request.getTeam2Score()) {
            match.setWinner(match.getTeam1());
        } else if (request.getTeam2Score() > request.getTeam1Score()) {
            match.setWinner(match.getTeam2());
        } else {
            // Draw? Spec doesn't clarify. Pickleball usually no draws.
            // For now, leave winner null if draw, but set status completed.
        }

        matchRepository.save(match);

        if (match.getPool() != null) {
            updatePoolStandings(match);
            checkAndAdvancePoolWinners(match.getPool());
        } else if (match.getBracketRound() != null) {
            advanceInBracket(match);
        }

        return modelMapper.map(match, MatchDTO.class);
    }

    private void updatePoolStandings(Match match) {
        Pool pool = match.getPool();
        // Recalculate ALL standings for this pool from scratch to ensure mathematical
        // correctness
        // and avoid incremental drift (buggy logic).

        // 1. Fetch all matches for the pool freshly
        List<Match> poolMatches = matchRepository.findByPoolId(pool.getId());
        List<PoolStanding> standings = poolStandingRepository.findByPoolId(pool.getId());

        // 2. Reset all standings
        for (PoolStanding standing : standings) {
            standing.setWins(0);
            standing.setLosses(0);
            standing.setPointsFor(0);
            standing.setPointsAgainst(0);
            standing.setPointDifferential(0);
        }

        // 3. Re-apply scores from all COMPLETED matches
        for (Match m : poolMatches) {
            if (m.getStatus() == Match.Status.completed && m.getTeam1() != null && m.getTeam2() != null) {
                // Determine scores (safe unboxing)
                int s1 = m.getTeam1Score() != null ? m.getTeam1Score() : 0;
                int s2 = m.getTeam2Score() != null ? m.getTeam2Score() : 0;

                // Update Team 1
                updateStandingFromScratch(standings, m.getTeam1().getId(), s1, s2);
                // Update Team 2
                updateStandingFromScratch(standings, m.getTeam2().getId(), s2, s1);
            }
        }

        // 4. Save all standings
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

    private void checkAndAdvancePoolWinners(Pool pool) {
        // 1. Check if all matches in the pool are completed
        // Fetch fresh matches to ensure we see the latest status
        List<Match> poolMatches = matchRepository.findByPoolId(pool.getId());

        boolean allComplete = poolMatches.stream()
                .allMatch(m -> m.getStatus() == Match.Status.completed);

        if (!allComplete)
            return;

        // 2. Calculate Standings (Freshly fetched)
        List<PoolStanding> standings = poolStandingRepository.findByPoolId(pool.getId());

        // Sort standings to find Top 2
        // Sort criteria: Wins (desc), Point Diff (desc), Points For (desc)
        standings.sort((s1, s2) -> {
            if (s2.getWins() != s1.getWins())
                return s2.getWins() - s1.getWins();
            if (s2.getPointDifferential() != s1.getPointDifferential())
                return s2.getPointDifferential() - s1.getPointDifferential();
            return s2.getPointsFor() - s1.getPointsFor();
        });

        if (standings.size() < 1)
            return;

        Team seed1 = standings.get(0).getTeam();
        Team seed2 = standings.size() > 1 ? standings.get(1).getTeam() : null;

        // 3. Determine placement in Elimination Bracket
        Tournament tournament = pool.getTournament();
        List<Pool> allPools = tournament.getPools();
        allPools.sort(java.util.Comparator.comparing(Pool::getName));

        int poolIndex = allPools.indexOf(pool);
        int totalPools = allPools.size();

        // 4. Update the target matches
        List<Match> bracketMatches = matchRepository.findByTournamentId(tournament.getId());

        if (totalPools == 1) {
            // Case 1 Pool: Finals (Round 1, Pos 1)
            Match finalMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 1 && m.getBracketPosition() == 1)
                    .findFirst().orElse(null);

            if (finalMatch != null) {
                finalMatch.setTeam1(seed1);
                if (seed2 != null)
                    finalMatch.setTeam2(seed2);
                matchRepository.save(finalMatch);
            }
        } else {
            // Case > 1 Pool
            // Seed 1 placement (Match Pos = Pool Index + 1)
            int matchPosForSeed1 = poolIndex + 1;
            Match match1 = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 1 && m.getBracketPosition() == matchPosForSeed1)
                    .findFirst().orElse(null);

            if (match1 != null) {
                match1.setTeam1(seed1);
                matchRepository.save(match1);
            }

            // Seed 2 placement (Match Index = (poolIndex - 1 + N) % N)
            // Match Index is 0-based, Pos is 1-based
            int matchIndexForSeed2 = (poolIndex - 1 + totalPools) % totalPools;
            int matchPosForSeed2 = matchIndexForSeed2 + 1;

            Match match2 = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 1 && m.getBracketPosition() == matchPosForSeed2)
                    .findFirst().orElse(null);

            if (match2 != null && seed2 != null) {
                match2.setTeam2(seed2);
                matchRepository.save(match2);
            }
        }
    }

    private void advanceInBracket(Match match) {
        if (match.getWinner() == null)
            return;

        Integer currentRound = match.getBracketRound();
        Integer currentPos = match.getBracketPosition();

        int nextRound = currentRound + 1;
        int nextPos = (currentPos + 1) / 2;

        List<Match> potentialMatches = matchRepository.findByTournamentId(match.getTournament().getId());

        Match nextMatch = potentialMatches.stream()
                .filter(m -> m.getBracketRound() != null &&
                        m.getBracketRound() == nextRound &&
                        m.getBracketPosition() == nextPos)
                .findFirst()
                .orElse(null);

        if (nextMatch != null) {
            if (currentPos % 2 != 0) {
                nextMatch.setTeam1(match.getWinner());
            } else {
                nextMatch.setTeam2(match.getWinner());
            }
            matchRepository.save(nextMatch);
        }
    }
}
