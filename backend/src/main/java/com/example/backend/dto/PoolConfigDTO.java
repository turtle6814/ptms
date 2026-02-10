package com.example.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class PoolConfigDTO {
    private String name;
    private List<String> teamNames;
}
