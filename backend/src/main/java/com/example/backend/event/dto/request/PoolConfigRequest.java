package com.example.backend.event.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class PoolConfigRequest {
    private String name;
    private List<String> teamNames;
}
