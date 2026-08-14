package com.example.backend.enums;

public enum MatchStatus {
    PENDING, IN_PROGRESS,
    COMPLETED,
    FORFEIT,
    WALKOVER,
    // Grand-final reset game (double elim), marked done with no score when the winners'-bracket
    // champion sweeps game one - no second game needed.
    SKIPPED;

    public boolean isFinished() {
        return this == COMPLETED || this == FORFEIT || this == WALKOVER || this == SKIPPED;
    }
}
