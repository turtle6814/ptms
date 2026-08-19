package com.example.backend.user.service.impl;

import com.example.backend.enums.Role;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.NotFoundException;
import com.example.backend.exception.ValidationException;
import com.example.backend.user.entity.User;
import com.example.backend.user.mapper.UserMapper;
import com.example.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserServiceImpl service = new UserServiceImpl(userRepository, userMapper);

    private User admin;
    private User organizer;
    private User target;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(UUID.randomUUID()).username("admin").role(Role.ADMIN).build();
        organizer = User.builder().id(UUID.randomUUID()).username("org").role(Role.ORGANIZER).build();
        target = User.builder().id(UUID.randomUUID()).username("target").role(Role.USER).build();
        when(userMapper.toResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            com.example.backend.user.dto.response.UserResponse r = new com.example.backend.user.dto.response.UserResponse();
            r.setId(u.getId());
            r.setUsername(u.getUsername());
            r.setRole(u.getRole());
            return r;
        });
    }

    @Test
    void getAllUsersThrowsForNonAdmin() {
        when(userRepository.findByUsername("org")).thenReturn(Optional.of(organizer));
        assertThrows(ForbiddenException.class, () -> service.getAllUsers("org"));
    }

    @Test
    void getAllUsersReturnsListForAdmin() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(admin, organizer, target));

        assertEquals(3, service.getAllUsers("admin").size());
    }

    @Test
    void getRefereesThrowsForPlainUser() {
        when(userRepository.findByUsername("target")).thenReturn(Optional.of(target));
        assertThrows(ForbiddenException.class, () -> service.getReferees("target"));
    }

    @Test
    void updateRoleThrowsForNonAdmin() {
        when(userRepository.findByUsername("org")).thenReturn(Optional.of(organizer));
        assertThrows(ForbiddenException.class,
                () -> service.updateRole(target.getId(), Role.REFEREE, "org"));
    }

    @Test
    void updateRoleThrowsWhenChangingOwnRole() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        assertThrows(ValidationException.class,
                () -> service.updateRole(admin.getId(), Role.USER, "admin"));
    }

    @Test
    void updateRoleThrowsWhenTargetMissing() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.updateRole(missingId, Role.REFEREE, "admin"));
    }

    @Test
    void updateRoleSucceedsForAdminOnAnotherUser() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateRole(target.getId(), Role.REFEREE, "admin");
        assertEquals(Role.REFEREE, response.getRole());
    }
}
