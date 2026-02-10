package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.EventService;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventDTO>>> getAllEvents() {
        return ResponseEntity.ok(ApiResponse.success(eventService.getAllEvents()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventDTO>> createEvent(@RequestBody CreateEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(eventService.createEvent(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDTO>> getEventById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getEventById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventDTO>> updateEvent(@PathVariable UUID id,
            @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(ApiResponse.success(eventService.updateEvent(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable UUID id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/tournaments")
    public ResponseEntity<ApiResponse<List<TournamentDTO>>> getTournaments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(eventService.getTournaments(id)));
    }

    @PostMapping("/{eventId}/tournaments/{tournamentId}")
    public ResponseEntity<ApiResponse<EventDTO>> addTournament(@PathVariable UUID eventId,
            @PathVariable UUID tournamentId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.addTournamentToEvent(eventId, tournamentId)));
    }

    @DeleteMapping("/{eventId}/tournaments/{tournamentId}")
    public ResponseEntity<ApiResponse<EventDTO>> removeTournament(@PathVariable UUID eventId,
            @PathVariable UUID tournamentId) {
        return ResponseEntity.ok(ApiResponse.success(eventService.removeTournamentFromEvent(eventId, tournamentId)));
    }
}
