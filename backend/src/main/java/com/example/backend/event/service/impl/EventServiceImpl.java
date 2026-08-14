package com.example.backend.event.service.impl;

import com.example.backend.enums.EventFormat;
import com.example.backend.enums.EventStatus;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;
import com.example.backend.event.dto.CreateEventRequest;
import com.example.backend.event.dto.EventDTO;
import com.example.backend.event.dto.PoolConfigDTO;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.Pool;
import com.example.backend.event.entity.PoolEntry;
import com.example.backend.event.entity.Team;
import com.example.backend.event.mapper.EventMapper;
import com.example.backend.event.repository.EventRepository;
import com.example.backend.event.repository.PoolRepository;
import com.example.backend.event.repository.TeamRepository;
import com.example.backend.event.service.EventService;
import com.example.backend.match.dto.ScoreRulesDTO;
import com.example.backend.match.entity.BracketSlotSource;
import com.example.backend.match.entity.Match;
import com.example.backend.match.repository.MatchRepository;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.tournament.repository.TournamentRepository;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import com.example.backend.utils.BracketGenerator;
import com.example.backend.utils.SeriesAbBracketGenerator;
import lombok.RequiredArgsConstructor;
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
    private final EventMapper eventMapper;

    @Override
    public List<EventDTO> getAllEvents(String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return tournamentRepository.findByOwner(owner).stream()
                .flatMap(tournament -> tournament.getEvents().stream())
                .map(eventMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public EventDTO getEventById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
        return eventMapper.toDto(event);
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
        event.setAdvancementPerPool(request.getAdvancementPerPool() > 0 ? request.getAdvancementPerPool() : 2);
        event.setWildcardCount(request.getWildcardCount());
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

        // Now generate matches
        for (Pool pool : savedEvent.getPools()) {
            generateRoundRobinMatches(pool, savedEvent, allMatches, poolRules);
        }

        // Generate placeholder elimination bracket (Semis and Finals), unless the event skips
        // playoffs entirely
        if (savedEvent.getFormat() != EventFormat.ROUND_ROBIN_ONLY) {
            BracketGenerator.Result bracket = savedEvent.getFormat() == EventFormat.POOL_TO_SERIES_AB
                    ? SeriesAbBracketGenerator.generate(savedEvent, savedEvent.getPools(), playoffRules)
                    : BracketGenerator.generate(savedEvent, savedEvent.getPools(), playoffRules);
            allMatches.addAll(bracket.matches());
            // Cascade-persisted via Pool (like matches are via Event) rather than saved directly:
            // these BracketSlotSource rows have client-assigned UUIDs (needed so the generator
            // can wire pointers before anything is persisted), which would make a direct
            // repository.save() incorrectly attempt an UPDATE instead of an INSERT.
            for (BracketSlotSource source : bracket.bracketSlotSources()) {
                if (source.getSourcePool() != null) {
                    source.getSourcePool().getBracketSlotSources().add(source);
                } else {
                    // Wildcard-sourced slots have no pool to cascade through - cascade via the
                    // bracket match instead.
                    source.getBracketMatch().getBracketSlotSources().add(source);
                }
            }
        }

        savedEvent.setMatches(allMatches);
        eventRepository.save(savedEvent);

        return eventMapper.toDto(savedEvent);
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
}
