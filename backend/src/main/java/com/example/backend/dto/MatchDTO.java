package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MatchDTO {
    private UUID id;
    private UUID tournamentId;
    private UUID poolId;
    private Integer bracketRound;
    private Integer bracketPosition;
    private UUID team1Id;
    private UUID team2Id;
    private Integer team1Score;
    private Integer team2Score;
    private UUID winnerId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
