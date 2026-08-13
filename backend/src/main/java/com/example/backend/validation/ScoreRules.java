package com.example.backend.validation;

import com.example.backend.match.entity.Match;
import com.example.backend.exception.ValidationException;

public final class ScoreRules {

    private ScoreRules() {
    }

    public static void validate(Match match, int team1Score, int team2Score) {
        if (team1Score < 0 || team2Score < 0) {
            throw new ValidationException("Scores cannot be negative");
        }
        if (team1Score == team2Score) {
            throw new ValidationException("Match cannot end in a tie");
        }

        int target = match.getTargetScore();
        int cap = match.getScoreCap();
        boolean winByTwo = match.getWinByTwo();
        int winner = Math.max(team1Score, team2Score);
        int loser = Math.min(team1Score, team2Score);

        if (winner > cap) {
            throw new ValidationException("Winning score cannot exceed the cap of " + cap);
        }
        if (winner < target) {
            throw new ValidationException("Winning score must reach at least " + target);
        }
        if (winByTwo && winner < cap && winner - loser < 2) {
            throw new ValidationException("Win by two required (unless the cap of " + cap + " is reached)");
        }
    }
}
