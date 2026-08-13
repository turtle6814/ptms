package com.example.backend.event.dto;

import com.example.backend.dto.MatchDTO;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class EliminationBracketDTO {
    private UUID eventId;
    private List<BracketRoundDTO> rounds;
    private UUID champion;
    private MatchDTO thirdPlaceMatch;
    private UUID thirdPlaceTeamId;
}
