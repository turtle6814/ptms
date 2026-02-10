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
                    // Store round info if needed for display?
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
        // Simple 4-team bracket: 2 Semis, 1 Final, 1 3rd Place
        // Semis (Round 1)
        createPlaceholderMatch(tournament, 1, 1, allMatches);
        createPlaceholderMatch(tournament, 1, 2, allMatches);
        // Finals (Round 2)
        createPlaceholderMatch(tournament, 2, 1, allMatches); // Winner of Semis
        // 3rd Place (Round 2, position 2?) or just separate flag?
        // Spec has 'thirdPlaceMatch' field in DTO.
        // Let's use Round 100 for 3rd place to distinguish? Or Round 2 Position 2.
        Match thirdPlace = new Match();
        thirdPlace.setTournament(tournament);
        thirdPlace.setBracketRound(2);
        thirdPlace.setBracketPosition(2); // Convention: Pos 1 = Final, Pos 2 = 3rd Place
        thirdPlace.setStatus(Match.Status.pending);
        allMatches.add(thirdPlace);
    }

    private void createPlaceholderMatch(Tournament tournament, int round, int position, List<Match> allMatches) {
        Match match = new Match();
        match.setTournament(tournament);
        match.setBracketRound(round);
        match.setBracketPosition(position);
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

        // Populate Elimination Bracket DTO
        // Filter matches that are part of the bracket (have bracketRound)
        List<Match> bracketMatches = tournament.getMatches().stream()
                .filter(m -> m.getBracketRound() != null)
                .collect(Collectors.toList());

        if (!bracketMatches.isEmpty()) {
            EliminationBracketDTO bracketDTO = new EliminationBracketDTO();
            bracketDTO.setTournamentId(tournament.getId());

            // Champion logic
            // If the final match (Round 2, Pos 1) is completed, set champion
            Match finalMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 2 && m.getBracketPosition() == 1)
                    .findFirst().orElse(null);

            if (finalMatch != null && finalMatch.getWinner() != null) {
                bracketDTO.setChampion(finalMatch.getWinner().getId());
            }

            // Third place match
            Match thirdPlaceMatch = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 2 && m.getBracketPosition() == 2)
                    .findFirst().orElse(null);
            if (thirdPlaceMatch != null) {
                bracketDTO.setThirdPlaceMatch(modelMapper.map(thirdPlaceMatch, MatchDTO.class));
                if (thirdPlaceMatch.getWinner() != null) {
                    bracketDTO.setThirdPlaceTeamId(thirdPlaceMatch.getWinner().getId());
                }
            }

            // Group matches into rounds
            // Round 1: Semis
            // Round 2: Finals (Position 1 only for main bracket view, usually)

            List<BracketRoundDTO> roundDTOs = new ArrayList<>();

            // Round 1
            List<Match> round1Matches = bracketMatches.stream()
                    .filter(m -> m.getBracketRound() == 1)
                    .collect(Collectors.toList());
            if (!round1Matches.isEmpty()) {
                BracketRoundDTO r1 = new BracketRoundDTO();
                r1.setRoundNumber(1);
                r1.setName("Semifinals");
                r1.setMatches(round1Matches.stream()
                        .map(m -> modelMapper.map(m, MatchDTO.class))
                        .collect(Collectors.toList()));
                roundDTOs.add(r1);
            }

            // Round 2 (Finals) - Exclude 3rd place match from standard 'rounds' list if UI
            // handles it separately
            // But usually UI expects it. Let's include Finals match here.
            if (finalMatch != null) {
                BracketRoundDTO r2 = new BracketRoundDTO();
                r2.setRoundNumber(2);
                r2.setName("Finals");
                // Only add the main final match to the list
                r2.setMatches(List.of(modelMapper.map(finalMatch, MatchDTO.class)));
                roundDTOs.add(r2);
            }

            bracketDTO.setRounds(roundDTOs);
            dto.setEliminationBracket(bracketDTO);
        }

        return dto;
    }
}
