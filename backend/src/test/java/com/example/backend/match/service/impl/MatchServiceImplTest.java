package com.example.backend.match.service.impl;

import com.example.backend.enums.MatchStatus;
import com.example.backend.enums.Role;
import com.example.backend.event.entity.Event;
import com.example.backend.event.repository.EventRefereeRepository;
import com.example.backend.event.repository.EventRepository;
import com.example.backend.event.repository.PoolRepository;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.match.dto.ScoreRules;
import com.example.backend.match.entity.Match;
import com.example.backend.match.mapper.MatchMapper;
import com.example.backend.match.repository.BracketSlotSourceRepository;
import com.example.backend.match.repository.MatchRepository;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchServiceImplTest {

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final PoolRepository poolRepository = mock(PoolRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final BracketSlotSourceRepository bracketSlotSourceRepository = mock(BracketSlotSourceRepository.class);
    private final EventRefereeRepository eventRefereeRepository = mock(EventRefereeRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MatchMapper matchMapper = mock(MatchMapper.class);

    private final MatchServiceImpl service = new MatchServiceImpl(matchRepository, poolRepository, eventRepository,
            bracketSlotSourceRepository, eventRefereeRepository, userRepository, matchMapper);

    private User owner;
    private Event event;
    private Match match;

    @BeforeEach
    void setUp() {
        owner = User.builder().username("alice").role(Role.ORGANIZER).build();
        Tournament tournament = Tournament.builder().owner(owner).build();
        event = new Event();
        event.setId(UUID.randomUUID());
        event.setTournament(tournament);
        match = Match.builder().id(UUID.randomUUID()).event(event).status(MatchStatus.PENDING).build();
        when(matchRepository.findById(match.getId())).thenReturn(Optional.of(match));
    }

    @Test
    void updateRulesAllowedForOwner() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
        assertDoesNotThrow(() -> service.updateRules(match.getId(), new ScoreRules(), "alice"));
    }

    @Test
    void updateRulesAllowedForAdmin() {
        User admin = User.builder().username("root").role(Role.ADMIN).build();
        when(userRepository.findByUsername("root")).thenReturn(Optional.of(admin));
        assertDoesNotThrow(() -> service.updateRules(match.getId(), new ScoreRules(), "root"));
    }

    @Test
    void updateRulesAllowedForAssignedReferee() {
        User referee = User.builder().username("ref").role(Role.REFEREE).build();
        when(userRepository.findByUsername("ref")).thenReturn(Optional.of(referee));
        when(eventRefereeRepository.existsByEventIdAndRefereeUsername(event.getId(), "ref")).thenReturn(true);

        assertDoesNotThrow(() -> service.updateRules(match.getId(), new ScoreRules(), "ref"));
    }

    @Test
    void updateRulesForbiddenForUnrelatedUser() {
        User stranger = User.builder().username("mallory").role(Role.ORGANIZER).build();
        when(userRepository.findByUsername("mallory")).thenReturn(Optional.of(stranger));
        when(eventRefereeRepository.existsByEventIdAndRefereeUsername(event.getId(), "mallory")).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> service.updateRules(match.getId(), new ScoreRules(), "mallory"));
    }
}
