package com.example.backend.dto;

import lombok.Data;

@Data
public class ScoreRulesDTO {
    private Integer targetScore;
    private Boolean winByTwo;
    private Integer scoreCap;
}
