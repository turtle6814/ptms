package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TournamentDTO>>> getAllTournaments() {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getAllTournaments()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TournamentDTO>> createTournament(@RequestBody CreateTournamentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tournamentService.createTournament(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TournamentDTO>> getTournamentById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getTournamentById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTournament(@PathVariable UUID id) {
        tournamentService.deleteTournament(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{id}/third-place-match")
    public ResponseEntity<ApiResponse<TournamentDTO>> toggleThirdPlaceMatch(
            @PathVariable UUID id,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(ApiResponse.success(
                tournamentService.toggleThirdPlaceMatch(id, enabled)));
    }
}
