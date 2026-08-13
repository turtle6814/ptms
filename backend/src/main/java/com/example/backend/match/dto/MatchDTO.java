package com.example.backend.match.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MatchDTO {
    private UUID id;
    private UUID eventId;
    private UUID poolId;
    private Integer roundNumber;
    private Integer bracketRound;
    private Integer bracketPosition;
    private UUID winnerNextMatchId;
    private String winnerNextSlot;
    private UUID loserNextMatchId;
    private String loserNextSlot;
    private UUID team1Id;
    private UUID team2Id;
    private Integer team1Score;
    private Integer team2Score;
    private UUID winnerId;
    private String status;
    private Integer targetScore;
    private Boolean winByTwo;
    private Integer scoreCap;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
