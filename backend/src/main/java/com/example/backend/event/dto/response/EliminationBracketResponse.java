package com.example.backend.event.dto.response;

import com.example.backend.match.dto.response.MatchResponse;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class EliminationBracketResponse {
    private UUID eventId;
    private List<BracketRoundResponse> rounds;
    private UUID champion;
    private MatchResponse thirdPlaceMatch;
    private UUID thirdPlaceTeamId;
    // Only populated for POOL_TO_SERIES_AB events - the round-1-losers-only consolation bracket
    // deciding a "B champion" (this.champion on the nested response). Never has its own
    // consolationBracket/thirdPlaceMatch.
    private EliminationBracketResponse consolationBracket;
    // Only populated for POOL_TO_DOUBLE_ELIM events - the 2-game grand final (winners' champion
    // vs losers' champion). grandFinalGame2 is SKIPPED (no score) unless the losers' champion won
    // game 1 and forced a reset. this.champion reflects the true overall winner either way.
    private MatchResponse grandFinalGame1;
    private MatchResponse grandFinalGame2;
}
