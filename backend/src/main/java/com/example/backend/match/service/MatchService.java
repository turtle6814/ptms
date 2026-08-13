package com.example.backend.match.service;

import com.example.backend.match.dto.ForfeitRequest;
import com.example.backend.match.dto.MatchDTO;
import com.example.backend.match.dto.ScoreRulesDTO;
import com.example.backend.match.dto.ScoreUpdateRequest;

import java.util.UUID;

public interface MatchService {
    MatchDTO updateScore(UUID matchId, ScoreUpdateRequest request, String username);

    MatchDTO updateRules(UUID matchId, ScoreRulesDTO request, String username);

    MatchDTO recordForfeit(UUID matchId, ForfeitRequest request, String username);
}
