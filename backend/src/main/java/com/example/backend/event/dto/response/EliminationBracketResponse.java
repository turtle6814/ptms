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
    // Only populated for POOL_TO_DOUBLE_ELIM events - the 2-game grand final (winners' champion
    // vs losers' champion). grandFinalGame2 is SKIPPED (no score) unless the losers' champion won
    // game 1 and forced a reset. this.champion reflects the true overall winner either way.
    private MatchDTO grandFinalGame1;
    private MatchDTO grandFinalGame2;
}
