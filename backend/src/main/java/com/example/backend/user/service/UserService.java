package com.example.backend.user.service;

import com.example.backend.enums.Role;
import com.example.backend.user.dto.response.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {
    List<UserResponse> getAllUsers(String actingUsername);

    List<UserResponse> getReferees(String actingUsername);

    UserResponse updateRole(UUID userId, Role newRole, String actingUsername);
}
