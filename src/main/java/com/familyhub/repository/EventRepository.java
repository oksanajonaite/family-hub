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

    // Used when deleting an entire family — removes all events for the family
    void deleteAllByFamilyId(Long familyId);

    // Used by the event reminder scheduler — finds events across all families starting within a time window.
    // The window is typically [now+50min, now+65min] so each 15-minute cron run covers a non-overlapping slice.
    List<Event> findAllByStartsAtBetween(LocalDateTime from, LocalDateTime to);
}
