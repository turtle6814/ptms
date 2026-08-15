package com.example.backend.match.mapper;

import com.example.backend.match.dto.response.MatchResponse;
import com.example.backend.match.entity.Match;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchMapper {

    private final ModelMapper modelMapper;

    public MatchResponse toResponse(Match match) {
        return modelMapper.map(match, MatchResponse.class);
    }
}
