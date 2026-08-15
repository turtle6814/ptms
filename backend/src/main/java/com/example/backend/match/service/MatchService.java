package com.example.backend.match.service;

import com.example.backend.match.dto.request.ForfeitRequest;
import com.example.backend.match.dto.response.MatchResponse;
import com.example.backend.match.dto.ScoreRules;
import com.example.backend.match.dto.request.ScoreUpdateRequest;

import java.util.UUID;

public interface MatchService {
    MatchResponse updateScore(UUID matchId, ScoreUpdateRequest request, String username);

    MatchResponse updateRules(UUID matchId, ScoreRules request, String username);

    MatchResponse recordForfeit(UUID matchId, ForfeitRequest request, String username);
}
