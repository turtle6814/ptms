package com.example.backend.event.mapper;

import com.example.backend.enums.BracketType;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;
import com.example.backend.event.dto.response.BracketRoundResponse;
import com.example.backend.event.dto.response.EliminationBracketResponse;
import com.example.backend.event.dto.response.EventResponse;
import com.example.backend.event.dto.response.PoolResponse;
import com.example.backend.event.dto.response.TeamResponse;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.event.entity.PoolEntry;
import com.example.backend.event.entity.Team;
import com.example.backend.match.dto.response.MatchResponse;
import com.example.backend.match.entity.Match;
import com.example.backend.match.repository.MatchRepository;
import com.example.backend.utils.StandingsCalculator;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final ModelMapper modelMapper;
    private final MatchRepository matchRepository;

    public EventResponse toResponse(Event event) {
        EventResponse response = modelMapper.map(event, EventResponse.class);

        // Manual mapping: Event.teams and Pool.matches have no backing JPA collection
        // (removed as dead inverse collections) so ModelMapper can't auto-populate them -
        // both must be assembled here from PoolEntry/MatchRepository instead.
        if (event.getPools() != null && response.getPools() != null) {
            // Sort pools by name to ensure stable ordering (Pool A, Pool B, Pool C...)
            response.getPools().sort(java.util.Comparator.comparing(PoolResponse::getName));

            List<TeamResponse> allTeams = new ArrayList<>();

            for (int i = 0; i < event.getPools().size(); i++) {
                Pool pool = event.getPools().get(i);
                List<Team> poolTeams = teamsInPool(pool);
                // Find matching PoolResponse
                for (PoolResponse poolResponse : response.getPools()) {
                    if (poolResponse.getId().equals(pool.getId())) {
                        // Sort Teams by CreatedAt to respect input order
                        if (pool.getPoolEntries() != null) {
                            poolResponse.setTeamIds(poolTeams.stream()
                                    .sorted(java.util.Comparator.comparing(Team::getCreatedAt,
                                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                                    .map(Team::getId)
                                    .collect(Collectors.toList()));
                            allTeams.addAll(poolTeams.stream()
                                    .map(t -> modelMapper.map(t, TeamResponse.class))
                                    .toList());
                        }

                        List<Match> poolMatches = matchRepository.findByPoolId(pool.getId());

                        // Sort Matches: Round (asc), then CreatedAt (asc), then ID (asc) for stability
                        poolResponse.setMatches(poolMatches.stream()
                                .map(m -> modelMapper.map(m, MatchResponse.class))
                                .sorted(java.util.Comparator.comparing(MatchResponse::getRoundNumber,
                                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                        .thenComparing(MatchResponse::getCreatedAt,
                                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                        .thenComparing(MatchResponse::getId))
                                .collect(Collectors.toList()));

                        poolResponse.setStandings(StandingsCalculator.compute(poolTeams, poolMatches));
                        break;
                    }
                }
            }

            response.setTeams(allTeams);
        }

        // Populate Elimination Bracket response. WINNERS and LOSERS (Series A/B consolation)
        // matches are built into separate responses - each bracket's rounds restart at 1, and
        // mixing them into one match list would make the structural champion/3rd-place detection
        // below ambiguous (both brackets' finals look identical: terminal, not a loser-edge target).
        List<Match> allBracketMatches = matchRepository.findByEventIdAndMatchType(event.getId(), MatchType.BRACKET);
        List<Match> winnersMatches = allBracketMatches.stream()
                .filter(m -> m.getBracketType() == BracketType.WINNERS)
                .collect(Collectors.toList());

        if (!winnersMatches.isEmpty()) {
            EliminationBracketResponse bracketResponse = buildBracketResponse(event.getId(), winnersMatches);

            List<Match> consolationMatches = allBracketMatches.stream()
                    .filter(m -> m.getBracketType() == BracketType.LOSERS)
                    .collect(Collectors.toList());
            if (!consolationMatches.isEmpty()) {
                bracketResponse.setConsolationBracket(buildBracketResponse(event.getId(), consolationMatches));
            }

            // Double elimination's true champion comes from the grand final, not the winners
            // bracket's own final (whose winner might still lose the reset game) - override the
            // champion buildBracketResponse derived from the WINNERS-only match list above.
            List<Match> finalMatches = allBracketMatches.stream()
                    .filter(m -> m.getBracketType() == BracketType.FINAL)
                    .collect(Collectors.toList());
            if (!finalMatches.isEmpty()) {
                Match game1 = finalMatches.stream().filter(m -> m.getBracketRound() == 1).findFirst().orElseThrow();
                Match game2 = finalMatches.stream().filter(m -> m.getBracketRound() == 2).findFirst().orElseThrow();
                bracketResponse.setGrandFinalGame1(modelMapper.map(game1, MatchResponse.class));
                bracketResponse.setGrandFinalGame2(modelMapper.map(game2, MatchResponse.class));

                Match decider = game2.getStatus() == MatchStatus.COMPLETED ? game2 : game1;
                bracketResponse.setChampion(decider.getWinner() != null ? decider.getWinner().getId() : null);
            }

            response.setEliminationBracket(bracketResponse);
        }

        return response;
    }

    private EliminationBracketResponse buildBracketResponse(UUID eventId, List<Match> bracketMatches) {
        EliminationBracketResponse bracketResponse = new EliminationBracketResponse();
        bracketResponse.setEventId(eventId);

        // Find max round number to identify Finals
        int maxRound = bracketMatches.stream()
                .mapToInt(Match::getBracketRound)
                .max().orElse(0);

        // Champion/3rd-place logic: identify structurally via the winner/loser pointer graph
        // instead of a (maxRound, position) convention. The final is the terminal match
        // (nothing to advance to) that no other match's loser routes into; the 3rd-place
        // match (if any) is the terminal match that IS a loser-edge target.
        java.util.Set<UUID> loserEdgeTargetIds = bracketMatches.stream()
                .map(Match::getLoserNextMatch)
                .filter(m -> m != null)
                .map(Match::getId)
                .collect(Collectors.toSet());

        Match finalMatch = bracketMatches.stream()
                .filter(m -> m.getWinnerNextMatch() == null && !loserEdgeTargetIds.contains(m.getId()))
                .findFirst().orElse(null);

        if (finalMatch != null && finalMatch.getWinner() != null) {
            bracketResponse.setChampion(finalMatch.getWinner().getId());
        }

        Match thirdPlaceMatch = bracketMatches.stream()
                .filter(m -> m.getWinnerNextMatch() == null && loserEdgeTargetIds.contains(m.getId()))
                .findFirst().orElse(null);
        if (thirdPlaceMatch != null) {
            bracketResponse.setThirdPlaceMatch(modelMapper.map(thirdPlaceMatch, MatchResponse.class));
            if (thirdPlaceMatch.getWinner() != null) {
                bracketResponse.setThirdPlaceTeamId(thirdPlaceMatch.getWinner().getId());
            }
        }

        // Group matches into rounds
        List<BracketRoundResponse> roundResponses = new ArrayList<>();

        for (int r = 1; r <= maxRound; r++) {
            int currentRound = r;
            List<Match> roundMatches = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == currentRound)
                    .sorted(java.util.Comparator.comparing(Match::getBracketPosition))
                    .collect(Collectors.toList());

            // For the final round, exclude the 3rd place match from the main list
            if (currentRound == maxRound && thirdPlaceMatch != null) {
                roundMatches.removeIf(m -> m.getId().equals(thirdPlaceMatch.getId()));
            }

            if (!roundMatches.isEmpty()) {
                BracketRoundResponse roundResponse = new BracketRoundResponse();
                roundResponse.setRoundNumber(currentRound);
                roundResponse.setName(getRoundName(currentRound, maxRound));
                roundResponse.setMatches(roundMatches.stream()
                        .map(m -> modelMapper.map(m, MatchResponse.class))
                        .collect(Collectors.toList()));
                roundResponses.add(roundResponse);
            }
        }

        bracketResponse.setRounds(roundResponses);
        return bracketResponse;
    }

    private List<Team> teamsInPool(Pool pool) {
        return pool.getPoolEntries().stream().map(PoolEntry::getTeam).collect(Collectors.toList());
    }

    private String getRoundName(int roundNumber, int totalRounds) {
        if (roundNumber == totalRounds)
            return "Finals";
        if (roundNumber == totalRounds - 1)
            return "Semifinals";
        if (roundNumber == totalRounds - 2)
            return "Quarterfinals";
        return "Round " + roundNumber;
    }
}