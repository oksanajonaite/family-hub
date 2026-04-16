package com.familyhub.service;

import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.Event;
import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.RecurrenceType;
import com.familyhub.entity.enums.Role;
import com.familyhub.mapper.EventMapper;
import com.familyhub.repository.*;
import com.familyhub.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepository eventRepository;
    @Mock EventParticipantRepository eventParticipantRepository;
    @Mock FamilyRepository familyRepository;
    @Mock UserRepository userRepository;
    @Mock PetRepository petRepository;
    @Mock FamilyMemberRepository familyMemberRepository;
    @Mock EventMapper eventMapper;

    @InjectMocks EventService eventService;

    private static final Long FAMILY_ID = 1L;

    // --- combineDateTime ---

    @Test
    void combineDateTime_withTime_returnsCombinedDateTime() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        LocalTime time = LocalTime.of(14, 30);

        LocalDateTime result = eventService.combineDateTime(date, time);

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 15, 14, 30));
    }

    @Test
    void combineDateTime_withNullTime_returnsMidnight() {
        LocalDate date = LocalDate.of(2025, 6, 15);

        LocalDateTime result = eventService.combineDateTime(date, null);

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 15, 0, 0));
    }

    // --- buildEndsAt ---

    @Test
    void buildEndsAt_withNullDate_returnsNull() {
        LocalDateTime result = eventService.buildEndsAt(null, LocalTime.of(15, 0));

        assertThat(result).isNull();
    }

    @Test
    void buildEndsAt_withDateAndTime_returnsDateTime() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        LocalTime time = LocalTime.of(16, 0);

        LocalDateTime result = eventService.buildEndsAt(date, time);

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 15, 16, 0));
    }

    @Test
    void buildEndsAt_withDateButNoTime_returnsMidnight() {
        LocalDate date = LocalDate.of(2025, 6, 15);

        LocalDateTime result = eventService.buildEndsAt(date, null);

        assertThat(result).isEqualTo(LocalDateTime.of(2025, 6, 15, 0, 0));
    }

    // --- Recurrence expansion (tested via getVisibleFamilyEventsBetween) ---

    @Test
    void weeklyEvent_expandsCorrectOccurrencesWithinRange() {
        // Event starts Jan 1 (Wednesday) at 10:00, repeats every week
        LocalDateTime eventStart = LocalDateTime.of(2025, 1, 1, 10, 0);
        Event event = buildEvent(1L, eventStart, RecurrenceType.WEEKLY, null);

        // Calendar window: Jan 6 – Jan 20
        LocalDateTime from = LocalDateTime.of(2025, 1, 6, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2025, 1, 20, 23, 59, 59);
        setupRecurringMock(event);

        List<EventResponse> results = eventService.getVisibleFamilyEventsBetween(FAMILY_ID, from, to, mockParent());

        // Expected: Jan 8 and Jan 15 fall within the window
        assertThat(results).hasSize(2);
        assertThat(results.get(0).startsAt()).isEqualTo(LocalDateTime.of(2025, 1, 8, 10, 0));
        assertThat(results.get(1).startsAt()).isEqualTo(LocalDateTime.of(2025, 1, 15, 10, 0));
    }

    @Test
    void dailyEvent_expandsCorrectOccurrencesWithinRange() {
        // Event starts Jan 1 at 08:00, repeats every day
        LocalDateTime eventStart = LocalDateTime.of(2025, 1, 1, 8, 0);
        Event event = buildEvent(2L, eventStart, RecurrenceType.DAILY, null);

        // Window: Jan 3 – Jan 5 (3 days)
        LocalDateTime from = LocalDateTime.of(2025, 1, 3, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2025, 1, 5, 23, 59, 59);
        setupRecurringMock(event);

        List<EventResponse> results = eventService.getVisibleFamilyEventsBetween(FAMILY_ID, from, to, mockParent());

        assertThat(results).hasSize(3);
        assertThat(results.get(0).startsAt().toLocalDate()).isEqualTo(LocalDate.of(2025, 1, 3));
        assertThat(results.get(1).startsAt().toLocalDate()).isEqualTo(LocalDate.of(2025, 1, 4));
        assertThat(results.get(2).startsAt().toLocalDate()).isEqualTo(LocalDate.of(2025, 1, 5));
    }

    @Test
    void weeklyEvent_honorsRecurrenceUntil() {
        // Weekly event, but recurrenceUntil = Jan 12 — Jan 15 must NOT appear
        LocalDateTime eventStart = LocalDateTime.of(2025, 1, 1, 9, 0);
        LocalDate until = LocalDate.of(2025, 1, 12);
        Event event = buildEvent(3L, eventStart, RecurrenceType.WEEKLY, until);

        // Window: Jan 1 – Jan 31
        LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2025, 1, 31, 23, 59, 59);
        setupRecurringMock(event);

        List<EventResponse> results = eventService.getVisibleFamilyEventsBetween(FAMILY_ID, from, to, mockParent());

        // Jan 1 and Jan 8 are within the until date; Jan 15 is after it
        assertThat(results).hasSize(2);
        assertThat(results.get(0).startsAt().toLocalDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(results.get(1).startsAt().toLocalDate()).isEqualTo(LocalDate.of(2025, 1, 8));
    }

    @Test
    void privateEvent_isHiddenForOtherUsers() {
        // Event is private, created by user 99
        Family family = Family.builder().id(FAMILY_ID).build();
        User creator = new User();
        creator.setId(99L);

        Event event = Event.builder()
                .id(1L)
                .family(family)
                .startsAt(LocalDateTime.of(2025, 1, 10, 10, 0))
                .recurrenceType(RecurrenceType.NONE)
                .createdBy(creator)
                .privateEvent(true)
                .build();

        when(eventRepository.findAllByFamilyIdAndStartsAtBetweenOrderByStartsAtAsc(eq(FAMILY_ID), any(), any()))
                .thenReturn(List.of(event));
        when(eventRepository.findAllByFamilyIdAndRecurrenceTypeNot(FAMILY_ID, RecurrenceType.NONE))
                .thenReturn(List.of());

        // Different user (id=42) is logged in — not the creator
        // Only getId() is actually called by isVisible(); getFamilyId/getRole are not needed here
        CustomUserDetails otherUser = mock(CustomUserDetails.class);
        when(otherUser.getId()).thenReturn(42L);

        LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2025, 1, 31, 23, 59, 59);

        List<EventResponse> results = eventService.getVisibleFamilyEventsBetween(FAMILY_ID, from, to, otherUser);

        assertThat(results).isEmpty();
    }

    @Test
    void privateEvent_isVisibleToItsCreator() {
        // Same private event, but the creator themselves is logged in
        Family family = Family.builder().id(FAMILY_ID).build();
        User creator = new User();
        creator.setId(99L);

        Event event = Event.builder()
                .id(1L)
                .family(family)
                .startsAt(LocalDateTime.of(2025, 1, 10, 10, 0))
                .recurrenceType(RecurrenceType.NONE)
                .createdBy(creator)
                .privateEvent(true)
                .build();

        when(eventRepository.findAllByFamilyIdAndStartsAtBetweenOrderByStartsAtAsc(eq(FAMILY_ID), any(), any()))
                .thenReturn(List.of(event));
        when(eventRepository.findAllByFamilyIdAndRecurrenceTypeNot(FAMILY_ID, RecurrenceType.NONE))
                .thenReturn(List.of());
        when(eventParticipantRepository.findAllByEventIdIn(anyList())).thenReturn(List.of());
        when(eventMapper.toResponse(any(), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(stubResponse(event));

        CustomUserDetails creator2 = mock(CustomUserDetails.class);
        when(creator2.getId()).thenReturn(99L); // same id as event creator — isVisible() returns true

        LocalDateTime from = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime to   = LocalDateTime.of(2025, 1, 31, 23, 59, 59);

        List<EventResponse> results = eventService.getVisibleFamilyEventsBetween(FAMILY_ID, from, to, creator2);

        assertThat(results).hasSize(1);
    }

    // --- Helpers ---

    private Event buildEvent(Long id, LocalDateTime startsAt, RecurrenceType recurrenceType, LocalDate until) {
        Family family = Family.builder().id(FAMILY_ID).build();
        User creator = new User();
        creator.setId(1L);

        return Event.builder()
                .id(id)
                .family(family)
                .title("Test Event")
                .startsAt(startsAt)
                .recurrenceType(recurrenceType)
                .recurrenceUntil(until)
                .createdBy(creator)
                .privateEvent(false)
                .build();
    }

    // Sets up the repository and mapper mocks for recurring event expansion tests
    private void setupRecurringMock(Event event) {
        when(eventRepository.findAllByFamilyIdAndStartsAtBetweenOrderByStartsAtAsc(eq(FAMILY_ID), any(), any()))
                .thenReturn(List.of());
        when(eventRepository.findAllByFamilyIdAndRecurrenceTypeNot(FAMILY_ID, RecurrenceType.NONE))
                .thenReturn(List.of(event));
        when(eventParticipantRepository.findAllByEventIdIn(anyList()))
                .thenReturn(List.of());
        // The mapper returns a response that mirrors the event's actual startsAt,
        // so that withOccurrenceDate() can correctly shift it to each occurrence date
        when(eventMapper.toResponse(any(), anyList(), anyList(), anyList(), anyList()))
                .thenAnswer(inv -> stubResponse(inv.getArgument(0)));
    }

    private EventResponse stubResponse(Event e) {
        return new EventResponse(
                e.getId(), e.getTitle(), null,
                e.getStartsAt(), e.getEndsAt(),
                e.isPrivateEvent(), e.getRecurrenceType(), e.getRecurrenceUntil(),
                e.getCreatedBy().getId(),
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private CustomUserDetails mockParent() {
        CustomUserDetails parent = mock(CustomUserDetails.class);
        lenient().when(parent.getId()).thenReturn(1L);
        lenient().when(parent.getFamilyId()).thenReturn(FAMILY_ID);
        lenient().when(parent.getRole()).thenReturn(Role.PARENT);
        return parent;
    }
}
