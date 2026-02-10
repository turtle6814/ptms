package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import com.example.backend.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments/{tournamentId}/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PutMapping("/{matchId}/score")
    public ResponseEntity<ApiResponse<MatchDTO>> updateScore(
            @PathVariable UUID tournamentId,
            @PathVariable UUID matchId,
            @RequestBody ScoreUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(matchService.updateScore(matchId, request)));
    }
}
