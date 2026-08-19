package com.example.backend.event.service.impl;

import com.example.backend.enums.Role;
import com.example.backend.event.entity.Event;
import com.example.backend.event.entity.EventReferee;
import com.example.backend.event.mapper.EventMapper;
import com.example.backend.event.mapper.EventRefereeMapper;
import com.example.backend.event.repository.EventRefereeRepository;
import com.example.backend.event.repository.EventRepository;
import com.example.backend.event.repository.PoolRepository;
import com.example.backend.event.repository.TeamRepository;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ValidationException;
import com.example.backend.match.repository.BracketSlotSourceRepository;
import com.example.backend.match.repository.MatchRepository;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.tournament.repository.TournamentRepository;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceImplTest {

    private final EventRepository eventRepository = mock(EventRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PoolRepository poolRepository = mock(PoolRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final BracketSlotSourceRepository bracketSlotSourceRepository = mock(BracketSlotSourceRepository.class);
    private final EventRefereeRepository eventRefereeRepository = mock(EventRefereeRepository.class);
    private final EventMapper eventMapper = mock(EventMapper.class);
    private final EventRefereeMapper eventRefereeMapper = new EventRefereeMapper();

    private final EventServiceImpl service = new EventServiceImpl(eventRepository, tournamentRepository,
            userRepository, poolRepository, teamRepository, matchRepository, bracketSlotSourceRepository,
            eventRefereeRepository, eventMapper, eventRefereeMapper);

    private User owner;
    private User referee;
    private Tournament tournament;
    private Event event;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(UUID.randomUUID()).username("alice").role(Role.ORGANIZER).build();
        referee = User.builder().id(UUID.randomUUID()).username("ref").role(Role.REFEREE).build();
        tournament = Tournament.builder().owner(owner).build();
        event = new Event();
        event.setId(UUID.randomUUID());
        event.setTournament(tournament);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
    }

    @Test
    void assignRefereeSucceedsForOwner() {
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(eventRefereeRepository.existsByEventIdAndRefereeId(event.getId(), referee.getId())).thenReturn(false);
        when(eventRefereeRepository.save(any(EventReferee.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.assignReferee(event.getId(), referee.getId(), "alice");

        assertEquals(referee.getId(), response.getRefereeId());
        assertEquals(event.getId(), response.getEventId());
    }

    @Test
    void assignRefereeThrowsWhenTargetIsNotReferee() {
        User notReferee = User.builder().id(UUID.randomUUID()).username("plain").role(Role.USER).build();
        when(userRepository.findById(notReferee.getId())).thenReturn(Optional.of(notReferee));

        assertThrows(ValidationException.class,
                () -> service.assignReferee(event.getId(), notReferee.getId(), "alice"));
    }

    @Test
    void assignRefereeThrowsOnDuplicateAssignment() {
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(eventRefereeRepository.existsByEventIdAndRefereeId(event.getId(), referee.getId())).thenReturn(true);

        assertThrows(ValidationException.class,
                () -> service.assignReferee(event.getId(), referee.getId(), "alice"));
    }

    @Test
    void assignRefereeThrowsForNonOwnerNonAdmin() {
        User stranger = User.builder().username("mallory").role(Role.ORGANIZER).build();
        when(userRepository.findByUsername("mallory")).thenReturn(Optional.of(stranger));

        assertThrows(ForbiddenException.class,
                () -> service.assignReferee(event.getId(), referee.getId(), "mallory"));
    }

    @Test
    void assignRefereeSucceedsForAdminEvenWhenNotOwner() {
        User admin = User.builder().username("root").role(Role.ADMIN).build();
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(admin));
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(eventRefereeRepository.existsByEventIdAndRefereeId(event.getId(), referee.getId())).thenReturn(false);
        when(eventRefereeRepository.save(any(EventReferee.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.assignReferee(event.getId(), referee.getId(), "root"));
    }

    @Test
    void getAssignedEventsReturnsOnlyCallersAssignments() {
        EventReferee assignment = EventReferee.builder().event(event).referee(referee).build();
        when(eventRefereeRepository.findByRefereeUsername("ref")).thenReturn(List.of(assignment));
        when(eventMapper.toResponse(event)).thenReturn(new com.example.backend.event.dto.response.EventResponse());

        assertEquals(1, service.getAssignedEvents("ref").size());
    }
}
