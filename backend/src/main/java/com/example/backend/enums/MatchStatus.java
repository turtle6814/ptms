package com.example.backend.enums;

public enum MatchStatus {
    PENDING, IN_PROGRESS,
    COMPLETED,
    FORFEIT,
    WALKOVER,
    SKIPPED;

    public boolean isFinished() {
        return this == COMPLETED || this == FORFEIT || this == WALKOVER || this == SKIPPED;
    }
}
