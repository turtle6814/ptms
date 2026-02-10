package com.example.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class BracketRoundDTO {
    private int roundNumber;
    private String name;
    private List<MatchDTO> matches;
}
