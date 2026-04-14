package com.familyhub.repository;

import com.familyhub.entity.Event;
import com.familyhub.entity.enums.RecurrenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByFamilyIdOrderByStartsAtAsc(Long familyId);

    // Used by the calendar to load non-recurring events within the visible date range
    List<Event> findAllByFamilyIdAndStartsAtBetweenOrderByStartsAtAsc(
            Long familyId, LocalDateTime from, LocalDateTime to
    );

    // Used by the calendar to load all recurring events — occurrences are expanded in EventService
    List<Event> findAllByFamilyIdAndRecurrenceTypeNot(Long familyId, RecurrenceType recurrenceType);
}
