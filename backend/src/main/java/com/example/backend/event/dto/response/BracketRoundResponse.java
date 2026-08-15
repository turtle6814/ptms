package com.example.backend.event.dto.response;

import com.example.backend.match.dto.response.MatchResponse;
import lombok.Data;
import java.util.List;

@Data
public class BracketRoundResponse {
    private int roundNumber;
    private String name;
    private List<MatchResponse> matches;
}
