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
        List<Team> teams = pool.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                Match match = new Match();
                match.setTournament(tournament);
                match.setPool(pool);
                match.setTeam1(teams.get(i));
                match.setTeam2(teams.get(j));
                match.setStatus(Match.Status.pending);
                matchRepository.save(match); // Save immediately or add to list?
                // Better to save via repository if relying on IDs, but list + Cascade update
                // works too.
                // Since we passed allMatches list, let's add to it.
                // BUT, teams already have IDs? Yes, from first save.
                allMatches.add(match);
            }
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

        // Manual mapping for bracket if needed, but ModelMapper might handle simple
        // fields.
        // We need to construct the EliminationBracketDTO from matches.
        // For now, let ModelMapper do its best, and we can refine complex mappings.
        // Actually, EliminationBracketDTO needs to be built from the matches list.
        EliminationBracketDTO bracketDTO = new EliminationBracketDTO();
        bracketDTO.setTournamentId(tournament.getId());
        // Logic to group matches by round...
        // Leaving detailed bracket mapping for later or assuming frontend handles flat
        // list of matches too?
        // The DTO has 'eliminationBracket' field. I should populate it.

        return dto;
    }
}
