package com.example.backend.event.dto;

import com.example.backend.match.dto.MatchDTO;
import lombok.Data;
import java.util.List;

@Data
public class BracketRoundDTO {
    private int roundNumber;
    private String name;
    private List<MatchDTO> matches;
}
