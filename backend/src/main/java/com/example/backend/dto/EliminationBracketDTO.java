package com.example.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class EliminationBracketDTO {
    private UUID tournamentId;
    private List<BracketRoundDTO> rounds;
    private UUID champion;
    private MatchDTO thirdPlaceMatch;
    private UUID thirdPlaceTeamId;
}
