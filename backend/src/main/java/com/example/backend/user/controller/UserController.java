package com.example.backend.user.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.event.dto.response.EventResponse;
import com.example.backend.event.service.EventService;
import com.example.backend.user.dto.request.UpdateRoleRequest;
import com.example.backend.user.dto.response.UserResponse;
import com.example.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EventService eventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers(authentication.getName())));
    }

    @GetMapping("/referees")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getReferees(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(userService.getReferees(authentication.getName())));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateRole(@PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request, Authentication authentication) {
        return ResponseEntity
                .ok(ApiResponse.success(userService.updateRole(id, request.getRole(), authentication.getName())));
    }

    @GetMapping("/me/assigned-events")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getAssignedEvents(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getAssignedEvents(authentication.getName())));
    }
}
