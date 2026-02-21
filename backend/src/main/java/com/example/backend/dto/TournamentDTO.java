package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TournamentDTO {
    private UUID id;
    private UUID eventId;
    private String name;
    private String status;
    private List<TeamDTO> teams;
    private List<PoolDTO> pools;
    private EliminationBracketDTO eliminationBracket;
    private boolean hasThirdPlaceMatch;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
