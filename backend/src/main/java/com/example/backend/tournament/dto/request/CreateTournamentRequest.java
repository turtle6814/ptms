package com.example.backend.tournament.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CreateTournamentRequest {
    @NotBlank(message = "Tournament name is required")
    @Size(max = 255, message = "Tournament name must be at most 255 characters")
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
}
