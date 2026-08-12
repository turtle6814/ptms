package com.example.backend.enums;

public enum MatchStatus {
    PENDING, IN_PROGRESS,
    COMPLETED,
    FORFEIT,
    WALKOVER;

    public boolean isFinished() {
        return this == COMPLETED || this == FORFEIT || this == WALKOVER;
    }
}
