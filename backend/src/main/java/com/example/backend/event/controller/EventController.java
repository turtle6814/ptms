package com.example.backend.event.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.event.dto.request.AssignRefereeRequest;
import com.example.backend.event.dto.request.CreateEventRequest;
import com.example.backend.event.dto.response.EventRefereeResponse;
import com.example.backend.event.dto.response.EventResponse;
import com.example.backend.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> getAllEvents(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getAllEvents(authentication.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(@Valid @RequestBody CreateEventRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(eventService.createEvent(request, authentication.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEventById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getEventById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable UUID id, Authentication authentication) {
        eventService.deleteEvent(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/referees")
    public ResponseEntity<ApiResponse<List<EventRefereeResponse>>> getReferees(@PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getReferees(id, authentication.getName())));
    }

    @PostMapping("/{id}/referees")
    public ResponseEntity<ApiResponse<EventRefereeResponse>> assignReferee(@PathVariable UUID id,
            @Valid @RequestBody AssignRefereeRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                eventService.assignReferee(id, request.getUserId(), authentication.getName())));
    }

    @DeleteMapping("/{id}/referees/{userId}")
    public ResponseEntity<ApiResponse<Void>> unassignReferee(@PathVariable UUID id, @PathVariable UUID userId,
            Authentication authentication) {
        eventService.unassignReferee(id, userId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
