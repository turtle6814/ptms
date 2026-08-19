package com.example.backend.user.service.impl;

import com.example.backend.enums.Role;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.NotFoundException;
import com.example.backend.exception.ValidationException;
import com.example.backend.user.dto.response.UserResponse;
import com.example.backend.user.entity.User;
import com.example.backend.user.mapper.UserMapper;
import com.example.backend.user.repository.UserRepository;
import com.example.backend.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Override
    public List<UserResponse> getAllUsers(String actingUsername) {
        requireAdmin(actingUsername);
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserResponse> getReferees(String actingUsername) {
        User actor = findByUsername(actingUsername);
        if (actor.getRole() != Role.ORGANIZER && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only organizers or admins can view referees");
        }
        return userRepository.findByRole(Role.REFEREE).stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse updateRole(UUID userId, Role newRole, String actingUsername) {
        User actor = findByUsername(actingUsername);
        if (actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can change a user's role");
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getUsername().equals(actingUsername)) {
            throw new ValidationException("Cannot change your own role");
        }
        target.setRole(newRole);
        return userMapper.toResponse(userRepository.save(target));
    }

    private void requireAdmin(String username) {
        if (findByUsername(username).getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only admins can perform this action");
        }
    }

    private User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}
