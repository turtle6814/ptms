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
    private final TournamentRepository tournamentRepository; // Added
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

        matchRepository.saveAndFlush(match);

        try {
            if (match.getPool() != null) {
                updatePoolStandings(match);
                checkAndAdvancePoolWinners(match.getPool());
            } else if (match.getBracketRound() != null) {
                advanceInBracket(match);
            }

            // Check and update tournament lifecycle status
            updateTournamentStatus(match.getTournament());
            tournamentRepository.save(match.getTournament());

        } catch (Exception e) {
            // Log error but don't fail the score update
            System.err.println("Error advancing tournament state: " + e.getMessage());
            e.printStackTrace();
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

        // Mark the pool as complete and persist it
        pool.setComplete(true);
        poolRepository.save(pool);

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

        // Fetch all pools freshly and sort by name to ensure consistent index
        List<Pool> allPools = poolRepository.findByTournamentId(tournament.getId());
        allPools.sort(java.util.Comparator.comparing(Pool::getName));

        int poolIndex = -1;
        for (int i = 0; i < allPools.size(); i++) {
            if (allPools.get(i).getId().equals(pool.getId())) {
                poolIndex = i;
                break;
            }
        }

        if (poolIndex == -1) {
            System.err.println("Could not find pool index for pool: " + pool.getName());
            return;
        }

        int totalPools = allPools.size();

        // 4. Update the target matches
        // IMPORTANT: Filter for matches that belong to the elimination bracket (pool is
        // null)
        // to avoid accidentally picking up pool matches which also have bracketRound
        // numbers.
        List<Match> bracketMatches = matchRepository.findByTournamentId(tournament.getId()).stream()
                .filter(m -> m.getPool() == null)
                .toList();

        if (totalPools == 1) {
            // Case 1 Pool: Finals (Round 1, Pos 1)
            Match finalMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 1 && m.getBracketPosition() == 1)
                    .findFirst().orElse(null);

            if (finalMatch != null) {
                finalMatch.setTeam1(seed1);
                if (seed2 != null)
                    finalMatch.setTeam2(seed2);

                finalMatch.setStatus(Match.Status.pending); // Ensure pending
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
        if (match.getWinner() == null || match.getBracketRound() == null || match.getBracketPosition() == null)
            return;

        int currentRound = match.getBracketRound();
        int currentPos = match.getBracketPosition();

        // 1. Calculate Next Match Position
        int nextRound = currentRound + 1;
        int nextPos = (currentPos + 1) / 2;

        // Fetch only elimination matches (where pool is null) to avoid mixing with pool
        // matches
        List<Match> allEliminationMatches = matchRepository.findByTournamentId(match.getTournament().getId())
                .stream()
                .filter(m -> m.getPool() == null)
                .toList();

        Match nextMatch = allEliminationMatches.stream()
                .filter(m -> m.getBracketRound() != null &&
                        m.getBracketRound() == nextRound &&
                        m.getBracketPosition() != null &&
                        m.getBracketPosition() == nextPos)
                .findFirst()
                .orElse(null);

        if (nextMatch != null) {
            // 2. Advance Team
            if (currentPos % 2 != 0) { // Odd position (1, 3, 5...) -> Team 1
                nextMatch.setTeam1(match.getWinner());

                // 3. Check for BYE scenario (Frontend Logic)
                // If current round has ODD matches, and this is the LAST match,
                // and we just filled Team 1... verify if Team 2 feeder exists.

                long currentRoundMatchCount = allEliminationMatches.stream()
                        .filter(m -> m.getBracketRound() != null && m.getBracketRound() == currentRound)
                        .count();

                // If there is no opponent match (currentPos == count), then it's a bye
                if (currentPos == currentRoundMatchCount) {
                    // Bye detected!
                    nextMatch.setTeam2Score(0);
                    nextMatch.setTeam1Score(0);
                    nextMatch.setWinner(match.getWinner()); // Auto-win
                    nextMatch.setStatus(Match.Status.completed);

                    matchRepository.save(nextMatch);

                    // Recursively advance
                    advanceInBracket(nextMatch);
                    return;
                }

            } else { // Even position (2, 4, 6...) -> Team 2
                nextMatch.setTeam2(match.getWinner());
            }
            matchRepository.save(nextMatch);
        }

        // 4. Populate 3rd place match with the loser from this match
        // The 3rd place match is in the same round as the Finals (maxRound), position
        // 2.
        // We add the loser if this match feeds into the Finals (i.e., nextRound is the
        // finals round).
        populateThirdPlaceMatch(match, allEliminationMatches);
    }

    private void populateThirdPlaceMatch(Match match, List<Match> allEliminationMatches) {
        if (match.getWinner() == null)
            return;

        int currentRound = match.getBracketRound();

        // Find the max round number (Finals round)
        int maxRound = allEliminationMatches.stream()
                .filter(m -> m.getBracketRound() != null)
                .mapToInt(Match::getBracketRound)
                .max().orElse(0);

        // This match must be in the round just before the Finals (i.e., Semifinals)
        if (currentRound != maxRound - 1)
            return;

        // Find the 3rd place match: same round as Finals (maxRound), position 2
        Match thirdPlaceMatch = allEliminationMatches.stream()
                .filter(m -> m.getBracketRound() != null &&
                        m.getBracketRound() == maxRound &&
                        m.getBracketPosition() != null &&
                        m.getBracketPosition() == 2)
                .findFirst().orElse(null);

        if (thirdPlaceMatch == null)
            return;

        // Determine the loser
        Team loser = match.getTeam1().getId().equals(match.getWinner().getId())
                ? match.getTeam2()
                : match.getTeam1();

        if (loser == null)
            return;

        // Place the loser in the 3rd place match
        if (thirdPlaceMatch.getTeam1() == null) {
            thirdPlaceMatch.setTeam1(loser);
        } else if (thirdPlaceMatch.getTeam2() == null) {
            thirdPlaceMatch.setTeam2(loser);
        }
        matchRepository.save(thirdPlaceMatch);
    }

    private void updateTournamentStatus(Tournament tournament) {
        // 1. Check for transition from POOL_PLAY to ELIMINATION
        if (tournament.getStatus() == Tournament.Status.pool_play) {
            boolean allPoolsComplete = poolRepository.findByTournamentId(tournament.getId()).stream()
                    .allMatch(Pool::isComplete);

            if (allPoolsComplete) {
                tournament.setStatus(Tournament.Status.elimination);
            }
        }

        // 2. Check for transition to COMPLETED
        if (tournament.getStatus() == Tournament.Status.elimination) {
            List<Match> eliminationMatches = matchRepository.findByTournamentId(tournament.getId()).stream()
                    .filter(m -> m.getPool() == null)
                    .toList();

            // Find the max round (Finals round)
            int maxRound = eliminationMatches.stream()
                    .filter(m -> m.getBracketRound() != null)
                    .mapToInt(Match::getBracketRound)
                    .max().orElse(0);

            // Exclude the 3rd place match (maxRound, position 2) from the completion check
            // The 3rd place match is optional and should not block tournament completion
            boolean allComplete = eliminationMatches.stream()
                    .filter(m -> !(m.getBracketRound() != null && m.getBracketRound() == maxRound
                            && m.getBracketPosition() != null && m.getBracketPosition() == 2))
                    .allMatch(m -> m.getStatus() == Match.Status.completed);

            if (allComplete && !eliminationMatches.isEmpty()) {
                tournament.setStatus(Tournament.Status.completed);
            }
        }
    }
}
