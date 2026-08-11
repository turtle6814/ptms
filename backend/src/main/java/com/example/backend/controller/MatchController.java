package com.example.backend.controller;

import com.example.backend.dto.ApiResponse;
import com.example.backend.dto.MatchDTO;
import com.example.backend.dto.ScoreUpdateRequest;
import com.example.backend.service.EventService;
import com.example.backend.service.MatchService;
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
    public ResponseEntity<ApiResponse<MatchDTO>> updateScore(
            @PathVariable UUID eventId,
            @PathVariable UUID matchId,
            @RequestBody ScoreUpdateRequest request,
            Authentication authentication) {

        // 1. Update the score (ownership verified inside the service)
        MatchDTO updatedMatch = matchService.updateScore(matchId, request, authentication.getName());

        // 2. Fetch the full updated event state
        var fullEvent = eventService.getEventById(eventId);

        // 3. Broadcast to subscribers
        messagingTemplate.convertAndSend("/topic/event/" + eventId, fullEvent);

        return ResponseEntity.ok(ApiResponse.success(updatedMatch));
    }
}
