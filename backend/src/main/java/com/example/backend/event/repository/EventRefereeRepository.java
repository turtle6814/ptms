package com.example.backend.event.repository;

import com.example.backend.event.entity.EventReferee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventRefereeRepository extends JpaRepository<EventReferee, UUID> {
    List<EventReferee> findByEventId(UUID eventId);

    List<EventReferee> findByRefereeUsername(String username);

    boolean existsByEventIdAndRefereeUsername(UUID eventId, String username);

    boolean existsByEventIdAndRefereeId(UUID eventId, UUID refereeId);

    void deleteByEventIdAndRefereeId(UUID eventId, UUID refereeId);
}
