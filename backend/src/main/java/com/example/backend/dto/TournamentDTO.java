package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TournamentDTO {
    private UUID id;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<UUID> eventIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
