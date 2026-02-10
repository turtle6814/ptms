package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import com.example.backend.service.MatchService;
import com.example.backend.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments/{tournamentId}/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final TournamentService tournamentService; // Needed to fetch full state
    private final SimpMessagingTemplate messagingTemplate;

    @PutMapping("/{matchId}/score")
    public ResponseEntity<ApiResponse<MatchDTO>> updateScore(
            @PathVariable UUID tournamentId,
            @PathVariable UUID matchId,
            @RequestBody ScoreUpdateRequest request) {

        // 1. Update the score
        MatchDTO updatedMatch = matchService.updateScore(matchId, request);

        // 2. Fetch the full updated tournament state
        // We broadcast the WHOLE tournament so the frontend can replace its state
        // safely
        // consistent with the previous polling behavior.
        var fullTournament = tournamentService.getTournamentById(tournamentId);

        // 3. Broadcast to subscribers
        messagingTemplate.convertAndSend("/topic/tournament/" + tournamentId, fullTournament);

        return ResponseEntity.ok(ApiResponse.success(updatedMatch));
    }
}
