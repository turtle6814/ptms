package com.example.backend.service.impl;

import com.example.backend.bracket.BracketGenerator;
import com.example.backend.dto.*;
import com.example.backend.entity.*;
import com.example.backend.enums.EventFormat;
import com.example.backend.enums.EventStatus;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;
import com.example.backend.repository.*;
import com.example.backend.service.EventService;
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
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final PoolRepository poolRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PoolStandingRepository poolStandingRepository;
    private final ModelMapper modelMapper;

    @Override
    public List<EventDTO> getAllEvents(String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return tournamentRepository.findByOwner(owner).stream()
                .flatMap(tournament -> tournament.getEvents().stream())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public EventDTO getEventById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        return convertToDTO(event);
    }

    @Override
    @Transactional
    public EventDTO createEvent(CreateEventRequest request, String username) {
        Tournament tournament = tournamentRepository.findById(request.getTournamentId())
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        verifyOwnership(tournament, username);

        Event event = new Event();
        event.setName(request.getName());
        event.setStatus(EventStatus.POOL_PLAY);
        event.setFormat(request.getFormat() != null ? request.getFormat() : EventFormat.POOL_TO_ELIM);
        event.setTournament(tournament);

        List<Pool> pools = new ArrayList<>();
        List<Team> allTeams = new ArrayList<>();
        List<Match> allMatches = new ArrayList<>();

        for (PoolConfigDTO poolConfig : request.getPools()) {
            Pool pool = new Pool();
            pool.setName(poolConfig.getName());
            pool.setEvent(event);

            List<PoolEntry> poolEntries = new ArrayList<>();
            for (String teamName : poolConfig.getTeamNames()) {
                Team team = new Team();
                team.setName(teamName);
                team.setEvent(event);
                allTeams.add(team);

                PoolEntry entry = new PoolEntry();
                entry.setPool(pool);
                entry.setTeam(team);
                poolEntries.add(entry);
            }
            pool.setPoolEntries(poolEntries);
            pools.add(pool);
        }

        event.setPools(pools);
        event.setTeams(allTeams);

        Event savedEvent = eventRepository.save(event);

        ScoreRulesDTO poolRules = resolveRules(request.getPoolStageRules(), 11, true, 15);
        ScoreRulesDTO playoffRules = resolveRules(request.getPlayoffStageRules(), 15, true, 21);

        // Now generate matches and standings
        for (Pool pool : savedEvent.getPools()) {
            generateRoundRobinMatches(pool, savedEvent, allMatches, poolRules);
            initializeStandings(pool);
        }

        // Generate placeholder elimination bracket (Semis and Finals), unless the event skips
        // playoffs entirely
        if (savedEvent.getFormat() != EventFormat.ROUND_ROBIN_ONLY) {
            BracketGenerator.Result bracket = BracketGenerator.generate(savedEvent, savedEvent.getPools(), playoffRules);
            allMatches.addAll(bracket.matches());
            // Cascade-persisted via Pool (like matches are via Event) rather than saved directly:
            // these BracketSlotSource rows have client-assigned UUIDs (needed so the generator
            // can wire pointers before anything is persisted), which would make a direct
            // repository.save() incorrectly attempt an UPDATE instead of an INSERT.
            for (BracketSlotSource source : bracket.bracketSlotSources()) {
                source.getSourcePool().getBracketSlotSources().add(source);
            }
        }

        savedEvent.setMatches(allMatches);
        eventRepository.save(savedEvent);

        return convertToDTO(savedEvent);
    }

    private ScoreRulesDTO resolveRules(ScoreRulesDTO override, int defaultTarget, boolean defaultWinByTwo,
            int defaultCap) {
        ScoreRulesDTO resolved = new ScoreRulesDTO();
        resolved.setTargetScore(override != null && override.getTargetScore() != null
                ? override.getTargetScore() : defaultTarget);
        resolved.setWinByTwo(override != null && override.getWinByTwo() != null
                ? override.getWinByTwo() : defaultWinByTwo);
        resolved.setScoreCap(override != null && override.getScoreCap() != null
                ? override.getScoreCap() : defaultCap);
        return resolved;
    }

    private void stampRules(Match match, ScoreRulesDTO rules) {
        match.setTargetScore(rules.getTargetScore());
        match.setWinByTwo(rules.getWinByTwo());
        match.setScoreCap(rules.getScoreCap());
    }

    private List<Team> teamsInPool(Pool pool) {
        return pool.getPoolEntries().stream().map(PoolEntry::getTeam).collect(Collectors.toList());
    }

    private void generateRoundRobinMatches(Pool pool, Event event, List<Match> allMatches, ScoreRulesDTO rules) {
        List<Team> teams = new ArrayList<>(teamsInPool(pool));
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
                    match.setEvent(event);
                    match.setPool(pool);
                    match.setTeam1(home);
                    match.setTeam2(away);
                    match.setStatus(MatchStatus.PENDING);
                    match.setMatchType(MatchType.POOL);
                    match.setRoundNumber(round + 1); // Store round number (1-based)
                    stampRules(match, rules);

                    matchRepository.save(match);
                    allMatches.add(match);
                }
            }

            // Rotate teams: Keep index 0 fixed, rotate the rest clockwise
            Team last = teams.remove(teams.size() - 1);
            teams.add(1, last);
        }
    }

    private void initializeStandings(Pool pool) {
        for (Team team : teamsInPool(pool)) {
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

    @Override
    public void deleteEvent(UUID id, String username) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        verifyOwnership(event.getTournament(), username);
        eventRepository.deleteById(id);
    }

    private void verifyOwnership(Tournament tournament, String username) {
        if (tournament.getOwner() == null || !tournament.getOwner().getUsername().equals(username)) {
            throw new RuntimeException("You do not have permission to modify this tournament's events");
        }
    }

    private EventDTO convertToDTO(Event event) {
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

                        // Sort Standings: Wins (desc), PointDiff (desc), PointsFor (desc)
                        if (poolDTO.getStandings() != null) {
                            poolDTO.getStandings().sort((s1, s2) -> {
                                if (s2.getWins() != s1.getWins())
                                    return s2.getWins() - s1.getWins();
                                if (s2.getPointDifferential() != s1.getPointDifferential())
                                    return s2.getPointDifferential() - s1.getPointDifferential();
                                return s2.getPointsFor() - s1.getPointsFor();
                            });
                        }
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
