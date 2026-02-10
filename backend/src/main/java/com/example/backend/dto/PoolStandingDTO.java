package com.example.backend.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class PoolStandingDTO {
    private UUID teamId;
    private String teamName;
    private int wins;
    private int losses;
    private int pointsFor;
    private int pointsAgainst;
    private int pointDifferential;
}
