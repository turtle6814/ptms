package com.example.backend.tournament.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateTournamentRequest {
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
}
