package com.example.backend.tournament.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.event.dto.response.EventResponse;
import com.example.backend.tournament.dto.request.CreateTournamentRequest;
import com.example.backend.tournament.dto.response.TournamentResponse;
import com.example.backend.tournament.dto.request.UpdateTournamentRequest;
import com.example.backend.tournament.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TournamentResponse>>> getAllTournaments(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getAllTournaments(authentication.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TournamentResponse>> createTournament(@RequestBody CreateTournamentRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tournamentService.createTournament(request, authentication.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TournamentResponse>> getTournamentById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getTournamentById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TournamentResponse>> updateTournament(@PathVariable UUID id,
            @RequestBody UpdateTournamentRequest request, Authentication authentication) {
        return ResponseEntity
                .ok(ApiResponse.success(tournamentService.updateTournament(id, request, authentication.getName())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTournament(@PathVariable UUID id, Authentication authentication) {
        tournamentService.deleteTournament(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getEvents(id)));
    }
}
