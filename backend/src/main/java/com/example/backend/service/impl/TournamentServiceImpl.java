package com.example.backend.service.impl;

import com.example.backend.dto.*;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {

    private final TournamentRepository tournamentRepository;
    private final EventRepository eventRepository;
    private final PoolRepository poolRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PoolStandingRepository poolStandingRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<TournamentDTO> getAllTournaments() {
        return tournamentRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public TournamentDTO getTournamentById(UUID id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        return convertToDTO(tournament);
    }

    @Override
    @Transactional
    public TournamentDTO createTournament(CreateTournamentRequest request) {
        Tournament tournament = new Tournament();
        tournament.setName(request.getName());
        tournament.setStatus(Tournament.Status.pool_play);

        if (request.getEventId() != null) {
            Event event = eventRepository.findById(request.getEventId())
                    .orElseThrow(() -> new RuntimeException("Event not found"));
            tournament.setEvent(event);
        }

        // We need to save tournament first to get ID for cascading (or rely on
        // CascadeType.ALL)
        // Linking everything carefully.

        List<Pool> pools = new ArrayList<>();
        List<Team> allTeams = new ArrayList<>();
        List<Match> allMatches = new ArrayList<>();

        for (PoolConfigDTO poolConfig : request.getPools()) {
            Pool pool = new Pool();
            pool.setName(poolConfig.getName());
            pool.setTournament(tournament);

            List<Team> poolTeams = new ArrayList<>();
            for (String teamName : poolConfig.getTeamNames()) {
                Team team = new Team();
                team.setName(teamName);
                team.setTournament(tournament);
                team.setPool(pool);
                poolTeams.add(team);
                allTeams.add(team);
            }
            pool.setTeams(poolTeams);
            pools.add(pool);
        }

        tournament.setPools(pools);
        tournament.setTeams(allTeams);

        // Save now to generate IDs for Teams and Pools, which are needed for Matches
        // and Standings
        Tournament savedTournament = tournamentRepository.save(tournament);

        // Now generate matches and standings
        for (Pool pool : savedTournament.getPools()) {
            generateRoundRobinMatches(pool, savedTournament, allMatches);
            initializeStandings(pool);
        }

        // Generate placeholder elimination bracket (Semis and Finals)
        generateEliminationBracket(savedTournament, allMatches);

        savedTournament.setMatches(allMatches);
        tournamentRepository.save(savedTournament);

        return convertToDTO(savedTournament);
    }

    private void generateRoundRobinMatches(Pool pool, Tournament tournament, List<Match> allMatches) {
        List<Team> teams = new ArrayList<>(pool.getTeams());
        int n = teams.size();

        if (n < 2)
            return;

        // Berger Table (Circle Method)
        // If odd number of teams, add a dummy team
        if (n % 2 != 0) {
            teams.add(null); // Dummy team
            n++;
        }

        int rounds = n - 1;
        int matchesPerRound = n / 2;

        for (int round = 0; round < rounds; round++) {
            for (int matchIndex = 0; matchIndex < matchesPerRound; matchIndex++) {
                Team home = teams.get(matchIndex);
                Team away = teams.get(n - 1 - matchIndex);

                // If neither team is dummy, schedule match
                if (home != null && away != null) {
                    Match match = new Match();
                    match.setTournament(tournament);
                    match.setPool(pool);
                    match.setTeam1(home);
                    match.setTeam2(away);
                    match.setStatus(Match.Status.pending);
                    match.setBracketRound(round + 1); // Store round number (1-based)
                    // We don't have explicit pool round field, but order of creation often
                    // suffices.
                    // Could add metadata if requirements strictly demand "Round 1" label in UI.

                    matchRepository.save(match);
                    allMatches.add(match);
                }
            }

            // Rotate teams: Keep index 0 fixed, rotate the rest clockwise
            // [0, 1, 2, 3] -> [0, 3, 1, 2]
            Team last = teams.remove(teams.size() - 1);
            teams.add(1, last);
        }
    }

    private void initializeStandings(Pool pool) {
        for (Team team : pool.getTeams()) {
            PoolStanding standing = new PoolStanding();
            standing.setPool(pool);
            standing.setTeam(team);
            standing.setWins(0);
            standing.setLosses(0);
            standing.setPointsFor(0);
            standing.setPointsAgainst(0);
            standing.setPointDifferential(0);
            poolStandingRepository.save(standing);
        }
    }

    private void generateEliminationBracket(Tournament tournament, List<Match> allMatches) {
        // Calculate number of teams advancing to playoffs (Top 2 from each pool)
        int numPools = tournament.getPools().size();
        int numAdvancing = numPools * 2;

        if (numAdvancing < 2)
            return; // No playoffs if fewer than 2 teams

        // Determine bracket size (next power of 2)
        int bracketSize = 1;
        while (bracketSize < numAdvancing) {
            bracketSize *= 2;
        }

        int matchesInRound = bracketSize / 2;
        int roundNumber = 1;

        // Generate rounds until we have a single final match
        while (matchesInRound >= 1) {
            for (int i = 0; i < matchesInRound; i++) {
                createPlaceholderMatch(tournament, roundNumber, i + 1, allMatches);
            }
            matchesInRound /= 2;
            roundNumber++;
        }

        // Add 3rd Place Match if we have at least Semifinals (Bracket Size >= 4)
        // The main loop generates rounds 1 to log2(bracketSize).
        // Total rounds = log2(bracketSize).
        // If total rounds >= 2, we have semis.
        // The last round generated (roundNumber - 1) is Finals.
        int totalRounds = roundNumber - 1;
        if (totalRounds >= 2) {
            Match thirdPlace = new Match();
            thirdPlace.setTournament(tournament);
            thirdPlace.setBracketRound(totalRounds); // Same round number as Finals? Or distinct?
            // Convention: Finals is usually the last round. 3rd place often considered same
            // "stage" just different match.
            // Using same round number as Finals for simplicity in grouping, but distinct
            // position or flag.
            // Our DTO mapping checks for (Round == Max && Pos == 2).
            // In the loop, Finals is (Round = totalRounds, Pos = 1).
            // So we can set 3rd place to (Round = totalRounds, Pos = 2).
            thirdPlace.setBracketRound(totalRounds);
            thirdPlace.setBracketPosition(2);
            thirdPlace.setStatus(Match.Status.pending);
            allMatches.add(thirdPlace);
        }
    }

    private void createPlaceholderMatch(Tournament tournament, int round, int position, List<Match> allMatches) {
        Match match = new Match();
        match.setTournament(tournament);
        match.setBracketRound(round);
        match.setBracketPosition(position); // 1-based index in the round
        match.setStatus(Match.Status.pending);
        allMatches.add(match);
    }

    @Override
    public void deleteTournament(UUID id) {
        if (!tournamentRepository.existsById(id)) {
            throw new RuntimeException("Tournament not found");
        }
        tournamentRepository.deleteById(id);
    }

    private TournamentDTO convertToDTO(Tournament tournament) {
        TournamentDTO dto = modelMapper.map(tournament, TournamentDTO.class);

        // Manual mapping for Pool Team IDs and Sort Matches
        if (tournament.getPools() != null && dto.getPools() != null) {
            for (int i = 0; i < tournament.getPools().size(); i++) {
                Pool pool = tournament.getPools().get(i);
                // Find matching PoolDTO
                for (PoolDTO poolDTO : dto.getPools()) {
                    if (poolDTO.getId().equals(pool.getId())) {
                        // Map Team IDs
                        if (pool.getTeams() != null) {
                            poolDTO.setTeamIds(pool.getTeams().stream()
                                    .map(Team::getId)
                                    .collect(Collectors.toList()));
                        }

                        // Sort Matches: Round (asc), then CreatedAt (asc)
                        if (poolDTO.getMatches() != null) {
                            poolDTO.getMatches().sort(java.util.Comparator.comparing(MatchDTO::getBracketRound,
                                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                    .thenComparing(MatchDTO::getCreatedAt,
                                            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
                        }
                        break;
                    }
                }
            }
        }

        // Populate Elimination Bracket DTO
        List<Match> bracketMatches = tournament.getMatches().stream()
                .filter(m -> m.getPool() == null && m.getBracketRound() != null)
                .collect(Collectors.toList());

        if (!bracketMatches.isEmpty()) {
            EliminationBracketDTO bracketDTO = new EliminationBracketDTO();
            bracketDTO.setTournamentId(tournament.getId());

            // Find max round number to identify Finals
            int maxRound = bracketMatches.stream()
                    .mapToInt(Match::getBracketRound)
                    .max().orElse(0);

            // Champion logic (Finals is maxRound, Pos 1)
            Match finalMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == maxRound && m.getBracketPosition() == 1)
                    .findFirst().orElse(null);

            if (finalMatch != null && finalMatch.getWinner() != null) {
                bracketDTO.setChampion(finalMatch.getWinner().getId());
            }

            // Third place match (maxRound, Pos 2)
            Match thirdPlaceMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == maxRound && m.getBracketPosition() == 2)
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
                        .sorted(java.util.Comparator.comparing(Match::getBracketPosition)) // Ensure stable order (Pos
                                                                                           // 1, 2...)
                        .collect(Collectors.toList());

                // For the final round, exclude the 3rd place match from the main list if
                // desired (usually handled separately)
                if (currentRound == maxRound && thirdPlaceMatch != null) {
                    roundMatches.removeIf(m -> m.getBracketPosition() == 2);
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
