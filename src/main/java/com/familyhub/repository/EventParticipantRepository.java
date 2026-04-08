package com.familyhub.repository;

import com.familyhub.entity.EventParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    List<EventParticipant> findAllByEventId(Long eventId); //vidi dalyviai konkreciam eventui
    void deleteAllByEventId(Long eventId); //istrina visus to event dalyvius

}
