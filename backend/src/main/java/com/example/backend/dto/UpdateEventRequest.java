package com.example.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateEventRequest {
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
}
