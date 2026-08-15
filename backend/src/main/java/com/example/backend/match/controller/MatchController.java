package com.example.backend.match.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.event.service.EventService;
import com.example.backend.match.dto.request.ForfeitRequest;
import com.example.backend.match.dto.response.MatchResponse;
import com.example.backend.match.dto.ScoreRules;
import com.example.backend.match.dto.request.ScoreUpdateRequest;
import com.example.backend.match.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final EventService eventService; // Needed to fetch full state
    private final SimpMessagingTemplate messagingTemplate;

    @PutMapping("/{matchId}/score")
    public ResponseEntity<ApiResponse<MatchResponse>> updateScore(
            @PathVariable UUID eventId,
            @PathVariable UUID matchId,
            @RequestBody ScoreUpdateRequest request,
            Authentication authentication) {

        // 1. Update the score (ownership verified inside the service)
        MatchResponse updatedMatch = matchService.updateScore(matchId, request, authentication.getName());

        // 2. Fetch the full updated event state
        var fullEvent = eventService.getEventById(eventId);

        // 3. Broadcast to subscribers
        messagingTemplate.convertAndSend("/topic/event/" + eventId, fullEvent);

        return ResponseEntity.ok(ApiResponse.success(updatedMatch));
    }

    @PatchMapping("/{matchId}/rules")
    public ResponseEntity<ApiResponse<MatchResponse>> updateRules(
            @PathVariable UUID eventId,
            @PathVariable UUID matchId,
            @RequestBody ScoreRules request,
            Authentication authentication) {

        MatchResponse updatedMatch = matchService.updateRules(matchId, request, authentication.getName());

        var fullEvent = eventService.getEventById(eventId);
        messagingTemplate.convertAndSend("/topic/event/" + eventId, fullEvent);

        return ResponseEntity.ok(ApiResponse.success(updatedMatch));
    }

    @PutMapping("/{matchId}/forfeit")
    public ResponseEntity<ApiResponse<MatchResponse>> recordForfeit(
            @PathVariable UUID eventId,
            @PathVariable UUID matchId,
            @RequestBody ForfeitRequest request,
            Authentication authentication) {

        MatchResponse updatedMatch = matchService.recordForfeit(matchId, request, authentication.getName());

        var fullEvent = eventService.getEventById(eventId);
        messagingTemplate.convertAndSend("/topic/event/" + eventId, fullEvent);

        return ResponseEntity.ok(ApiResponse.success(updatedMatch));
    }
}
