package com.example.backend.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class PoolConfigRequest {
    @NotBlank(message = "Pool name is required")
    @Size(max = 255, message = "Pool name must be at most 255 characters")
    private String name;
    private List<@NotBlank(message = "Team name is required") @Size(max = 255, message = "Team name must be at most 255 characters") String> teamNames;
}
