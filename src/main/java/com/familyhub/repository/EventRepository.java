package com.familyhub.repository;

import com.familyhub.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByFamilyIdOrderByStartsAtAsc(Long familyId); //visi family eventai nuo ankstyviausio
    List<Event> findAllByFamilyIdAndStartsAtBetweenOrderByStartsAtAsc(
            Long familyId, LocalDateTime from, LocalDateTime to //event datos intervale
    );
}
