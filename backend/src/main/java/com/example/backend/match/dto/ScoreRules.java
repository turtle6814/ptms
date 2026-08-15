package com.example.backend.match.dto;

import lombok.Data;

@Data
public class ScoreRulesDTO {
    private Integer targetScore;
    private Boolean winByTwo;
    private Integer scoreCap;
}
