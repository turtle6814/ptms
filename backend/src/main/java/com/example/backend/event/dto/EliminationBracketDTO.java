package com.example.backend.event.dto;

import com.example.backend.match.dto.MatchDTO;
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
    // Only populated for POOL_TO_SERIES_AB events - the round-1-losers-only consolation bracket
    // deciding a "B champion" (this.champion on the nested DTO). Never has its own
    // consolationBracket/thirdPlaceMatch.
    private EliminationBracketDTO consolationBracket;
}
