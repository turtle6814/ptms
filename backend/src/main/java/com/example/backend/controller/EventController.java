package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.EventService;
import lombok.*;
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
    public ResponseEntity<ApiResponse<List<EventDTO>>> getAllEvents(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getAllEvents(authentication.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventDTO>> createEvent(@RequestBody CreateEventRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(eventService.createEvent(request, authentication.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDTO>> getEventById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getEventById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDTO>> updateEvent(@PathVariable UUID id,
            @RequestBody UpdateEventRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(eventService.updateEvent(id, request, authentication.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable UUID id, Authentication authentication) {
        eventService.deleteEvent(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/tournaments")
    public ResponseEntity<ApiResponse<List<TournamentDTO>>> getTournaments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getTournaments(id)));
    }

    @PostMapping("/{eventId}/tournaments/{tournamentId}")
    public ResponseEntity<ApiResponse<EventDTO>> addTournament(@PathVariable UUID eventId,
            @PathVariable UUID tournamentId, Authentication authentication) {
        return ResponseEntity.ok(
                ApiResponse
                        .success(eventService.addTournamentToEvent(eventId, tournamentId, authentication.getName())));
    }

    @DeleteMapping("/{eventId}/tournaments/{tournamentId}")
    public ResponseEntity<ApiResponse<EventDTO>> removeTournament(@PathVariable UUID eventId,
            @PathVariable UUID tournamentId, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse
                .success(eventService.removeTournamentFromEvent(eventId, tournamentId, authentication.getName())));
    }
}
