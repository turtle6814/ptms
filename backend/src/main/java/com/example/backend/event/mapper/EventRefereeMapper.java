package com.example.backend.event.mapper;

import com.example.backend.event.dto.response.EventRefereeResponse;
import com.example.backend.event.entity.EventReferee;
import org.springframework.stereotype.Component;

@Component
public class EventRefereeMapper {

    public EventRefereeResponse toResponse(EventReferee entity) {
        EventRefereeResponse response = new EventRefereeResponse();
        response.setId(entity.getId());
        response.setEventId(entity.getEvent().getId());
        response.setRefereeId(entity.getReferee().getId());
        response.setRefereeUsername(entity.getReferee().getUsername());
        response.setAssignedAt(entity.getAssignedAt());
        return response;
    }
}
