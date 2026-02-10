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
        } else if (match.getBracketRound() != null) {
            advanceInBracket(match);
        }

        return modelMapper.map(match, MatchDTO.class);
    }

    private void updatePoolStandings(Match match) {
        Pool pool = match.getPool();
        updateStanding(pool, match.getTeam1(), match.getTeam1Score(), match.getTeam2Score());
        updateStanding(pool, match.getTeam2(), match.getTeam2Score(), match.getTeam1Score());

        // Check if pool is complete logic can be added here
    }

    private void updateStanding(Pool pool, Team team, int scored, int allowed) {
        PoolStanding standing = poolStandingRepository.findByPoolIdAndTeamId(pool.getId(), team.getId())
                .orElseThrow(() -> new RuntimeException("Standing not found"));

        standing.setPointsFor(standing.getPointsFor() + scored);
        standing.setPointsAgainst(standing.getPointsAgainst() + allowed);
        standing.setPointDifferential(standing.getPointsFor() - standing.getPointsAgainst());

        if (scored > allowed) {
            standing.setWins(standing.getWins() + 1);
        } else if (scored < allowed) {
            standing.setLosses(standing.getLosses() + 1);
        }

        poolStandingRepository.save(standing);
    }

    private void advanceInBracket(Match match) {
        if (match.getWinner() == null)
            return;

        Integer currentRound = match.getBracketRound();
        Integer currentPos = match.getBracketPosition();

        // Logic: Winner goes to match in Round + 1, Match Position = (CurrentPos + 1) /
        // 2
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
            // Determine if team 1 or team 2
            if (currentPos % 2 != 0) { // Odd position (1, 3, 5...) -> Team 1
                nextMatch.setTeam1(match.getWinner());
            } else { // Even position (2, 4, 6...) -> Team 2
                nextMatch.setTeam2(match.getWinner());
            }
            matchRepository.save(nextMatch);
        }
    }
}
