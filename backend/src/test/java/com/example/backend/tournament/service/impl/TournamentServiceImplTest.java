package com.example.backend.tournament.service.impl;

import com.example.backend.enums.Role;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.tournament.dto.request.CreateTournamentRequest;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.tournament.mapper.TournamentMapper;
import com.example.backend.tournament.repository.TournamentRepository;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TournamentServiceImplTest {

    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ModelMapper modelMapper = mock(ModelMapper.class);
    private final TournamentMapper tournamentMapper = mock(TournamentMapper.class);
    private final TournamentServiceImpl service =
            new TournamentServiceImpl(tournamentRepository, userRepository, modelMapper, tournamentMapper);

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"USER", "REFEREE"})
    void createTournamentThrowsForNonOrganizerRoles(Role role) {
        User user = User.builder().username("bob").role(role).build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class,
                () -> service.createTournament(new CreateTournamentRequest(), "bob"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ORGANIZER", "ADMIN"})
    void createTournamentSucceedsForOrganizerAndAdmin(Role role) {
        User user = User.builder().username("bob").role(role).build();
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(modelMapper.map(any(), eq(Tournament.class))).thenReturn(new Tournament());
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.createTournament(new CreateTournamentRequest(), "bob"));
    }
}
