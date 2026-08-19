package com.example.backend.base;

import com.example.backend.enums.Role;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.NotFoundException;
import com.example.backend.tournament.entity.Tournament;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseServiceTest {

    @SuppressWarnings("unchecked")
    private final JpaRepository<String, UUID> repository = mock(JpaRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BaseService<String, UUID, String> service =
            new BaseService<>(repository, s -> s, "Widget", userRepository) {
    };

    @Test
    void findByIdOrThrowThrowsNotFoundExceptionWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class, () -> service.findByIdOrThrow(id));
        assertThrows(NotFoundException.class, () -> service.getById(id));
        org.junit.jupiter.api.Assertions.assertEquals("Widget not found", ex.getMessage());
    }

    @Test
    void verifyOwnershipThrowsForbiddenExceptionWhenUsernameDoesNotMatchOwner() {
        Tournament tournament = Tournament.builder()
                .owner(User.builder().username("alice").build())
                .build();

        assertThrows(ForbiddenException.class, () -> service.verifyOwnership(tournament, "bob"));
    }

    @Test
    void verifyOwnershipThrowsForbiddenExceptionWhenOwnerIsNull() {
        Tournament tournament = Tournament.builder().owner(null).build();

        assertThrows(ForbiddenException.class, () -> service.verifyOwnership(tournament, "bob"));
    }

    @Test
    void verifyOwnershipPassesWhenUsernameMatchesOwner() {
        Tournament tournament = Tournament.builder()
                .owner(User.builder().username("alice").build())
                .build();

        assertDoesNotThrow(() -> service.verifyOwnership(tournament, "alice"));
    }

    @Test
    void verifyOwnershipPassesForAdminEvenWhenNotOwner() {
        Tournament tournament = Tournament.builder()
                .owner(User.builder().username("alice").build())
                .build();
        when(userRepository.findByUsername("bob"))
                .thenReturn(Optional.of(User.builder().username("bob").role(Role.ADMIN).build()));

        assertDoesNotThrow(() -> service.verifyOwnership(tournament, "bob"));
    }

    @Test
    void isAdminReturnsFalseForUnknownUsername() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertFalse(service.isAdmin("ghost"));
    }
}
