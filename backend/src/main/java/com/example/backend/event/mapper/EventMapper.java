package com.example.backend.event.mapper;

import com.example.backend.dto.MatchDTO;
import com.example.backend.entity.Match;
import com.example.backend.enums.MatchType;
import com.example.backend.event.dto.BracketRoundDTO;
import com.example.backend.event.dto.EliminationBracketDTO;
import com.example.backend.event.dto.EventDTO;
import com.example.backend.event.dto.PoolDTO;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.event.entity.PoolEntry;
import com.example.backend.event.entity.Team;
import com.example.backend.repository.MatchRepository;
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

    public EventDTO toDto(Event event) {
        EventDTO dto = modelMapper.map(event, EventDTO.class);

        // Manual mapping for Pool Team IDs and Sort Matches
        if (event.getPools() != null && dto.getPools() != null) {
            // Sort pools by name to ensure stable ordering (Pool A, Pool B, Pool C...)
            dto.getPools().sort(java.util.Comparator.comparing(PoolDTO::getName));

            for (int i = 0; i < event.getPools().size(); i++) {
                Pool pool = event.getPools().get(i);
                // Find matching PoolDTO
                for (PoolDTO poolDTO : dto.getPools()) {
                    if (poolDTO.getId().equals(pool.getId())) {
                        // Sort Teams by CreatedAt to respect input order
                        if (pool.getPoolEntries() != null) {
                            poolDTO.setTeamIds(teamsInPool(pool).stream()
                                    .sorted(java.util.Comparator.comparing(Team::getCreatedAt,
                                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                                    .map(Team::getId)
                                    .collect(Collectors.toList()));
                        }

                        // Sort Matches: Round (asc), then CreatedAt (asc), then ID (asc) for stability
                        if (poolDTO.getMatches() != null) {
                            poolDTO.getMatches().sort(java.util.Comparator.comparing(MatchDTO::getRoundNumber,
                                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                    .thenComparing(MatchDTO::getCreatedAt,
                                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                    .thenComparing(MatchDTO::getId));
                        }

                        poolDTO.setStandings(StandingsCalculator.compute(
                                teamsInPool(pool), matchRepository.findByPoolId(pool.getId())));
                        break;
                    }
                }
            }
        }

        // Populate Elimination Bracket DTO
        List<Match> bracketMatches = event.getMatches().stream()
                .filter(m -> m.getMatchType() == MatchType.BRACKET)
                .collect(Collectors.toList());

        if (!bracketMatches.isEmpty()) {
            EliminationBracketDTO bracketDTO = new EliminationBracketDTO();
            bracketDTO.setEventId(event.getId());

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
                bracketDTO.setChampion(finalMatch.getWinner().getId());
            }

            Match thirdPlaceMatch = bracketMatches.stream()
                    .filter(m -> m.getWinnerNextMatch() == null && loserEdgeTargetIds.contains(m.getId()))
                    .findFirst().orElse(null);
            if (thirdPlaceMatch != null) {
                bracketDTO.setThirdPlaceMatch(modelMapper.map(thirdPlaceMatch, MatchDTO.class));
                if (thirdPlaceMatch.getWinner() != null) {
                    bracketDTO.setThirdPlaceTeamId(thirdPlaceMatch.getWinner().getId());
                }
            }

            // Group matches into rounds
            List<BracketRoundDTO> roundDTOs = new ArrayList<>();

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
                    BracketRoundDTO roundDTO = new BracketRoundDTO();
                    roundDTO.setRoundNumber(currentRound);
                    roundDTO.setName(getRoundName(currentRound, maxRound));
                    roundDTO.setMatches(roundMatches.stream()
                            .map(m -> modelMapper.map(m, MatchDTO.class))
                            .collect(Collectors.toList()));
                    roundDTOs.add(roundDTO);
                }
            }

            bracketDTO.setRounds(roundDTOs);
            dto.setEliminationBracket(bracketDTO);
        }

        return dto;
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