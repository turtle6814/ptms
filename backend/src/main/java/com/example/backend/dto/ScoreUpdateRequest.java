package com.example.backend.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class ScoreUpdateRequest {
    private UUID matchId;
    private Integer team1Score;
    private Integer team2Score;
}
