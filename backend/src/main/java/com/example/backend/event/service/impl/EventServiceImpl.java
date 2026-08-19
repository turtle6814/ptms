package com.example.backend.event.service.impl;

import com.example.backend.base.BaseService;
import com.example.backend.enums.EventFormat;
import com.example.backend.enums.EventStatus;
import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.MatchType;
import com.example.backend.enums.Role;
import com.example.backend.event.dto.request.CreateEventRequest;
import com.example.backend.event.dto.response.EventRefereeResponse;
import com.example.backend.event.dto.response.EventResponse;
import com.example.backend.event.dto.request.PoolConfigRequest;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.EventReferee;
import com.example.backend.event.entity.Pool;
import com.example.backend.event.entity.PoolEntry;
import com.example.backend.event.entity.Team;
import com.example.backend.event.mapper.EventMapper;
import com.example.backend.event.mapper.EventRefereeMapper;
import com.example.backend.event.repository.EventRefereeRepository;
import com.example.backend.event.repository.EventRepository;
import com.example.backend.event.repository.PoolRepository;
import com.example.backend.event.repository.TeamRepository;
import com.example.backend.event.service.EventService;
import com.example.backend.exception.NotFoundException;
import com.example.backend.exception.ValidationException;
import com.example.backend.match.dto.ScoreRules;
import com.example.backend.match.entity.Match;
import com.example.backend.match.repository.BracketSlotSourceRepository;
import com.example.backend.match.repository.MatchRepository;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.tournament.repository.TournamentRepository;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import com.example.backend.utils.BracketGenerator;
import com.example.backend.utils.DoubleElimBracketGenerator;
import com.example.backend.utils.SeriesAbBracketGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EventServiceImpl extends BaseService<Event, UUID, EventResponse> implements EventService {

    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final PoolRepository poolRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final BracketSlotSourceRepository bracketSlotSourceRepository;
    private final EventRefereeRepository eventRefereeRepository;
    private final EventMapper eventMapper;
    private final EventRefereeMapper eventRefereeMapper;

    public EventServiceImpl(EventRepository eventRepository, TournamentRepository tournamentRepository,
                             UserRepository userRepository, PoolRepository poolRepository,
                             TeamRepository teamRepository, MatchRepository matchRepository,
                             BracketSlotSourceRepository bracketSlotSourceRepository,
                             EventRefereeRepository eventRefereeRepository,
                             EventMapper eventMapper, EventRefereeMapper eventRefereeMapper) {
        super(eventRepository, eventMapper::toResponse, "Event", userRepository);
        this.tournamentRepository = tournamentRepository;
        this.userRepository = userRepository;
        this.poolRepository = poolRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.bracketSlotSourceRepository = bracketSlotSourceRepository;
        this.eventRefereeRepository = eventRefereeRepository;
        this.eventMapper = eventMapper;
        this.eventRefereeMapper = eventRefereeMapper;
    }

    @Override
    public List<EventResponse> getAllEvents(String username) {
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return tournamentRepository.findByOwner(owner).stream()
                .flatMap(tournament -> tournament.getEvents().stream())
                .map(eventMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public EventResponse getEventById(UUID id) {
        return getById(id);
    }

    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, String username) {
        Tournament tournament = tournamentRepository.findById(request.getTournamentId())
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        verifyOwnership(tournament, username);

        Event event = new Event();
        event.setName(request.getName());
        event.setStatus(EventStatus.POOL_PLAY);
        event.setFormat(request.getFormat() != null ? request.getFormat() : EventFormat.POOL_TO_ELIMINATION);
        event.setAdvancementPerPool(request.getAdvancementPerPool() > 0 ? request.getAdvancementPerPool() : 2);
        event.setWildcardCount(request.getWildcardCount());
        event.setTournament(tournament);

        Event savedEvent = save(event);

        List<Pool> pools = new ArrayList<>();
        List<Team> allTeams = new ArrayList<>();

        for (PoolConfigRequest poolConfig : request.getPools()) {
            Pool pool = new Pool();
            pool.setName(poolConfig.getName());
            pool.setEvent(savedEvent);

            List<PoolEntry> poolEntries = new ArrayList<>();
            for (String teamName : poolConfig.getTeamNames()) {
                Team team = new Team();
                team.setName(teamName);
                team.setEvent(savedEvent);
                allTeams.add(team);

                PoolEntry entry = new PoolEntry();
                entry.setPool(pool);
                entry.setTeam(team);
                poolEntries.add(entry);
            }
            pool.setPoolEntries(poolEntries);
            pools.add(pool);
        }

        // Teams must be persisted before pools, since PoolEntry.team (cascaded via
        // Pool.poolEntries below) requires a non-transient Team to satisfy its FK.
        teamRepository.saveAll(allTeams);

        savedEvent.setPools(pools);
        savedEvent = save(savedEvent);

        ScoreRules poolRules = resolveRules(request.getPoolStageRules(), 11, true, 15);
        ScoreRules playoffRules = resolveRules(request.getPlayoffStageRules(), 15, true, 21);

        // Now generate matches
        for (Pool pool : savedEvent.getPools()) {
            generateRoundRobinMatches(pool, savedEvent, poolRules);
        }

        // Generate placeholder elimination bracket (Semis and Finals), unless the event skips
        // playoffs entirely
        if (savedEvent.getFormat() != EventFormat.ROUND_ROBIN_ONLY) {
            BracketGenerator.Result bracket = switch (savedEvent.getFormat()) {
                case POOL_TO_SERIES_AB -> SeriesAbBracketGenerator.generate(savedEvent, savedEvent.getPools(), playoffRules);
                case POOL_TO_DOUBLE_ELIMINATION -> DoubleElimBracketGenerator.generate(savedEvent, savedEvent.getPools(), playoffRules);
                default -> BracketGenerator.generate(savedEvent, savedEvent.getPools(), playoffRules);
            };
            matchRepository.saveAll(bracket.matches());
            bracketSlotSourceRepository.saveAll(bracket.bracketSlotSources());
        }

        return eventMapper.toResponse(savedEvent);
    }

    private ScoreRules resolveRules(ScoreRules override, int defaultTarget, boolean defaultWinByTwo,
            int defaultCap) {
        ScoreRules resolved = new ScoreRules();
        resolved.setTargetScore(override != null && override.getTargetScore() != null
                ? override.getTargetScore() : defaultTarget);
        resolved.setWinByTwo(override != null && override.getWinByTwo() != null
                ? override.getWinByTwo() : defaultWinByTwo);
        resolved.setScoreCap(override != null && override.getScoreCap() != null
                ? override.getScoreCap() : defaultCap);
        return resolved;
    }

    private void stampRules(Match match, ScoreRules rules) {
        match.setTargetScore(rules.getTargetScore());
        match.setWinByTwo(rules.getWinByTwo());
        match.setScoreCap(rules.getScoreCap());
    }

    private List<Team> teamsInPool(Pool pool) {
        return pool.getPoolEntries().stream().map(PoolEntry::getTeam).collect(Collectors.toList());
    }

    private void generateRoundRobinMatches(Pool pool, Event event, ScoreRules rules) {
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
                }
            }

            // Rotate teams: Keep index 0 fixed, rotate the rest clockwise
            Team last = teams.remove(teams.size() - 1);
            teams.add(1, last);
        }
    }

    @Override
    public void deleteEvent(UUID id, String username) {
        Event event = findByIdOrThrow(id);
        verifyOwnership(event.getTournament(), username);
        deleteById(id);
    }

    @Override
    public List<EventRefereeResponse> getReferees(UUID eventId, String actingUsername) {
        Event event = findByIdOrThrow(eventId);
        verifyOwnership(event.getTournament(), actingUsername);
        return eventRefereeRepository.findByEventId(eventId).stream()
                .map(eventRefereeMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public EventRefereeResponse assignReferee(UUID eventId, UUID userId, String actingUsername) {
        Event event = findByIdOrThrow(eventId);
        verifyOwnership(event.getTournament(), actingUsername);

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (targetUser.getRole() != Role.REFEREE) {
            throw new ValidationException("User must have the REFEREE role to be assigned");
        }
        if (eventRefereeRepository.existsByEventIdAndRefereeId(eventId, userId)) {
            throw new ValidationException("User is already assigned as a referee for this event");
        }

        EventReferee eventReferee = EventReferee.builder()
                .event(event)
                .referee(targetUser)
                .build();
        return eventRefereeMapper.toResponse(eventRefereeRepository.save(eventReferee));
    }

    @Override
    public void unassignReferee(UUID eventId, UUID userId, String actingUsername) {
        Event event = findByIdOrThrow(eventId);
        verifyOwnership(event.getTournament(), actingUsername);
        eventRefereeRepository.deleteByEventIdAndRefereeId(eventId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventResponse> getAssignedEvents(String username) {
        return eventRefereeRepository.findByRefereeUsername(username).stream()
                .map(eventReferee -> eventMapper.toResponse(eventReferee.getEvent()))
                .collect(Collectors.toList());
    }
}
