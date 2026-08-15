package com.example.backend.match.dto;

import lombok.Data;

@Data
public class ScoreRules {
    private Integer targetScore;
    private Boolean winByTwo;
    private Integer scoreCap;
}
