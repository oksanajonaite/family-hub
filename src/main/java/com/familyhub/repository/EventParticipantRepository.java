package com.familyhub.repository;

import com.familyhub.entity.EventParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

@Repository
public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {

    List<EventParticipant> findAllByEventId(Long eventId);

    // Loads participants for multiple events in a single query — avoids N+1 in list/calendar views
    List<EventParticipant> findAllByEventIdIn(List<Long> eventIds);

    void deleteAllByEventId(Long eventId); // used before re-saving participants on event update

    // Used before deleting a pet — removes all event participations so the FK constraint is not violated
    void deleteAllByPetId(Long petId);

    // Used before deleting a family member — removes all event participations so the FK constraint is not violated
    void deleteAllByFamilyMemberId(Long familyMemberId);

    // Used when deleting an entire family — clears all participant rows for a batch of events at once
    void deleteAllByEventIdIn(List<Long> eventIds);
}
