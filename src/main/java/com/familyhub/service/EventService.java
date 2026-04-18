package com.familyhub.service;

import com.familyhub.dto.request.event.CreateEventRequest;
import com.familyhub.dto.request.event.UpdateEventRequest;
import com.familyhub.dto.response.EventFormData;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.Event;
import com.familyhub.entity.EventParticipant;
import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.ParticipantType;
import com.familyhub.entity.enums.RecurrenceType;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.exception.EventNotFoundException;
import com.familyhub.mapper.EventMapper;
import com.familyhub.repository.EventParticipantRepository;
import com.familyhub.repository.EventRepository;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.PetRepository;
import com.familyhub.repository.UserRepository;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final PetRepository petRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final EventMapper eventMapper;

    // Returns events within the given date range — used for the calendar view.
    // Non-recurring events: fetched directly by date range.
    // Recurring events: fetched all, then expanded into virtual occurrences within the range.
    // Participants are loaded in a single batch query (findAllByEventIdIn) to avoid N+1.
    @Transactional(readOnly = true)
    public List<EventResponse> getVisibleFamilyEventsBetween(
            Long familyId, LocalDateTime from, LocalDateTime to, CustomUserDetails currentUser
    ) {
        // 1. Non-recurring events that fall directly within the range
        List<Event> nonRecurring = eventRepository
                .findAllByFamilyIdAndStartsAtBetweenOrderByStartsAtAsc(familyId, from, to)
                .stream()
                .filter(e -> e.getRecurrenceType() == RecurrenceType.NONE)
                .filter(e -> isVisible(e, currentUser))
                .toList();

        // 2. Recurring events — may have started before `from`, so we can't use the between query
        List<Event> recurring = eventRepository
                .findAllByFamilyIdAndRecurrenceTypeNot(familyId, RecurrenceType.NONE)
                .stream()
                .filter(e -> isVisible(e, currentUser))
                .filter(e -> !e.getStartsAt().isAfter(to))
                .toList();

        // 3. Batch-load all participants in one query instead of one query per event (avoids N+1)
        List<Long> allEventIds = new ArrayList<>();
        nonRecurring.forEach(e -> allEventIds.add(e.getId()));
        recurring.forEach(e -> allEventIds.add(e.getId()));

        Map<Long, List<EventParticipant>> participantsByEventId = allEventIds.isEmpty()
                ? Map.of()
                : eventParticipantRepository.findAllByEventIdIn(allEventIds)
                        .stream()
                        .collect(Collectors.groupingBy(p -> p.getEvent().getId()));

        // 4. Build responses
        List<EventResponse> result = new ArrayList<>();
        nonRecurring.forEach(e -> result.add(
                toEventResponse(e, participantsByEventId.getOrDefault(e.getId(), List.of()))
        ));
        recurring.forEach(e -> result.addAll(
                expandRecurring(e, participantsByEventId.getOrDefault(e.getId(), List.of()), from, to)
        ));

        result.sort(Comparator.comparing(EventResponse::startsAt));
        return result;
    }

    // Participants are loaded in a single batch query (findAllByEventIdIn) to avoid N+1.
    @Transactional(readOnly = true)
    public List<EventResponse> getVisibleFamilyEvents(Long familyId, CustomUserDetails currentUser) {
        List<Event> events = eventRepository.findAllByFamilyIdOrderByStartsAtAsc(familyId)
                .stream()
                .filter(e -> isVisible(e, currentUser))
                .toList();

        if (events.isEmpty()) return List.of();

        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, List<EventParticipant>> participantsByEventId = eventParticipantRepository
                .findAllByEventIdIn(eventIds)
                .stream()
                .collect(Collectors.groupingBy(p -> p.getEvent().getId()));

        return events.stream()
                .map(e -> toEventResponse(e, participantsByEventId.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long eventId, CustomUserDetails currentUser) {
        Event event = getEventBelongingToFamily(eventId, currentUser.getFamilyId());

        if (event.isPrivateEvent() && !event.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new EventNotFoundException(eventId);
        }

        return toEventResponse(event, eventParticipantRepository.findAllByEventId(eventId));
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request, CustomUserDetails currentUser) {
        Family family = familyRepository.findById(currentUser.getFamilyId())
                .orElseThrow(() -> new IllegalStateException("Family not found for current user"));

        User createdBy = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        Event event = eventMapper.toEntity(request);
        event.setFamily(family);
        event.setCreatedBy(createdBy);
        // Combine separate date + time fields into LocalDateTime
        event.setStartsAt(combineDateTime(request.startDate(), request.startTime()));
        event.setEndsAt(buildEndsAt(request.endDate(), request.endTime()));

        Event saved = eventRepository.save(event);
        List<EventParticipant> participants = saveParticipants(saved, request.participantIds(), currentUser.getFamilyId());

        return toEventResponse(saved, participants);
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request, CustomUserDetails currentUser) {
        Event event = getEventBelongingToFamily(eventId, currentUser.getFamilyId());

        boolean isCreator = event.getCreatedBy().getId().equals(currentUser.getId());
        boolean isParent = currentUser.getRole() == Role.PARENT;

        if (!isCreator && !isParent) {
            throw new AccessDeniedException();
        }

        eventMapper.updateEntity(request, event);
        // Combine separate date + time fields into LocalDateTime
        event.setStartsAt(combineDateTime(request.startDate(), request.startTime()));
        event.setEndsAt(buildEndsAt(request.endDate(), request.endTime()));

        Event saved = eventRepository.save(event);

        eventParticipantRepository.deleteAllByEventId(eventId);
        // flush() forces the DELETE to be sent to the DB before the INSERT,
        // preventing a unique constraint violation within the same transaction
        eventParticipantRepository.flush();
        List<EventParticipant> participants = saveParticipants(saved, request.participantIds(), currentUser.getFamilyId());

        return toEventResponse(saved, participants);
    }

    @Transactional
    public void deleteEvent(Long eventId, CustomUserDetails currentUser) {
        Event event = getEventBelongingToFamily(eventId, currentUser.getFamilyId());

        boolean isCreator = event.getCreatedBy().getId().equals(currentUser.getId());
        boolean isParent = currentUser.getRole() == Role.PARENT;

        if (!isCreator && !isParent) {
            throw new AccessDeniedException();
        }

        eventParticipantRepository.deleteAllByEventId(eventId);
        eventRepository.delete(event);
    }

    // Converts an EventResponse into an UpdateEventRequest for pre-populating the edit form.
    // Participant IDs are re-encoded with their type prefix (USER_, PET_, MEMBER_)
    // so the form checkboxes match the format sent on submit.
    public UpdateEventRequest toEditRequest(EventResponse event) {
        List<String> participantIds = new ArrayList<>();
        event.participantUserIds().forEach(uid -> participantIds.add("USER_" + uid));
        event.participantPetIds().forEach(pid -> participantIds.add("PET_" + pid));
        event.participantFamilyMemberIds().forEach(mid -> participantIds.add("MEMBER_" + mid));

        return new UpdateEventRequest(
                event.title(),
                event.description(),
                event.startsAt().toLocalDate(),
                event.startsAt().toLocalTime(),
                event.endsAt() != null ? event.endsAt().toLocalDate() : null,
                event.endsAt() != null ? event.endsAt().toLocalTime() : null,
                event.privateEvent(),
                event.recurrenceType(),
                event.recurrenceUntil(),
                participantIds
        );
    }

    // Assembles EventFormData for create/edit forms — loads all dropdown/checkbox options.
    // eventId and participantIds are null on the create form, populated on the edit form.
    @Transactional(readOnly = true)
    public EventFormData buildEventFormData(Long eventId, List<String> participantIds, Long familyId) {
        return new EventFormData(
                userRepository.findAllByFamilyId(familyId),
                petRepository.findAllByFamilyId(familyId),
                familyMemberRepository.findAllByFamilyId(familyId),
                List.of(RecurrenceType.values()),
                eventId,
                participantIds
        );
    }

    // --- Private helpers ---

    private Event getEventBelongingToFamily(Long eventId, Long familyId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (!event.getFamily().getId().equals(familyId)) {
            throw new EventNotFoundException(eventId);
        }
        return event;
    }

    private boolean isVisible(Event event, CustomUserDetails currentUser) {
        return !event.isPrivateEvent() || event.getCreatedBy().getId().equals(currentUser.getId());
    }

    // If no time is provided, default to midnight so the event still appears on the correct day
    // Package-private for unit testing
    LocalDateTime combineDateTime(LocalDate date, LocalTime time) {
        return date.atTime(time != null ? time : LocalTime.MIDNIGHT);
    }

    // Returns null if no end date is given — end is optional
    // Package-private for unit testing
    LocalDateTime buildEndsAt(LocalDate endDate, LocalTime endTime) {
        if (endDate == null) return null;
        return endDate.atTime(endTime != null ? endTime : LocalTime.MIDNIGHT);
    }

    // Expands a recurring event into virtual occurrences that fall within [from, to].
    // No new DB records are created — occurrences are built in memory from the base event.
    private List<EventResponse> expandRecurring(
            Event event,
            List<EventParticipant> participants,
            LocalDateTime from,
            LocalDateTime to
    ) {
        List<EventResponse> result = new ArrayList<>();
        EventResponse base = toEventResponse(event, participants);

        // Honor recurrenceUntil if set; otherwise expand up to the end of the calendar range
        LocalDateTime upperBound = (event.getRecurrenceUntil() != null)
                ? event.getRecurrenceUntil().atTime(23, 59, 59).isBefore(to)
                    ? event.getRecurrenceUntil().atTime(23, 59, 59)
                    : to
                : to;

        LocalDateTime cursor = event.getStartsAt();

        while (!cursor.isAfter(upperBound)) {
            if (!cursor.isBefore(from)) {
                result.add(withOccurrenceDate(base, cursor));
            }
            if (event.getRecurrenceType() == RecurrenceType.DAILY) {
                cursor = cursor.plusDays(1);
            } else if (event.getRecurrenceType() == RecurrenceType.WEEKLY) {
                cursor = cursor.plusWeeks(1);
            } else {
                // NONE should never reach here due to the filter in getVisibleFamilyEventsBetween
                cursor = upperBound.plusDays(1);
            }
        }

        return result;
    }

    // Creates a new EventResponse with an adjusted startsAt (and shifted endsAt if present).
    // Used to generate virtual calendar occurrences for recurring events.
    private EventResponse withOccurrenceDate(EventResponse base, LocalDateTime newStart) {
        LocalDateTime newEnd = null;
        if (base.endsAt() != null) {
            long seconds = Duration.between(base.startsAt(), base.endsAt()).getSeconds();
            newEnd = newStart.plusSeconds(seconds);
        }
        return new EventResponse(
                base.id(), base.title(), base.description(),
                newStart, newEnd,
                base.privateEvent(), base.recurrenceType(), base.recurrenceUntil(),
                base.createdByUserId(),
                base.participantUserIds(), base.participantPetIds(), base.participantFamilyMemberIds(),
                base.participantNames()
        );
    }

    // Builds the EventResponse from a saved event and its participant records
    private EventResponse toEventResponse(Event event, List<EventParticipant> participants) {
        return eventMapper.toResponse(
                event,
                extractUserIds(participants),
                extractPetIds(participants),
                extractFamilyMemberIds(participants),
                extractParticipantNames(participants)
        );
    }

    // Builds participant records from prefixed IDs and persists them; returns the saved list
    private List<EventParticipant> saveParticipants(Event event, List<String> participantIds, Long familyId) {
        List<EventParticipant> participants = buildParticipants(event, participantIds, familyId);
        eventParticipantRepository.saveAll(participants);
        return participants;
    }

    // Converts the prefixed string list into EventParticipant records.
    // "USER_42"   → ParticipantType.USER
    // "PET_7"     → ParticipantType.PET
    // "MEMBER_15" → ParticipantType.FAMILY_MEMBER
    // Only participants that belong to the same family are accepted (security check).
    private List<EventParticipant> buildParticipants(
            Event event, List<String> participantIds, Long familyId
    ) {
        if (participantIds == null || participantIds.isEmpty()) return List.of();

        List<Long> userIds = new ArrayList<>();
        List<Long> petIds = new ArrayList<>();
        List<Long> memberIds = new ArrayList<>();

        for (String participantId : participantIds) {
            if (participantId.startsWith("USER_")) {
                userIds.add(Long.parseLong(participantId.substring(5)));
            } else if (participantId.startsWith("PET_")) {
                petIds.add(Long.parseLong(participantId.substring(4)));
            } else if (participantId.startsWith("MEMBER_")) {
                memberIds.add(Long.parseLong(participantId.substring(7)));
            }
        }

        List<EventParticipant> participants = new ArrayList<>();

        userRepository.findAllById(userIds).stream()
                .filter(u -> u.getFamily() != null && familyId.equals(u.getFamily().getId()))
                .forEach(user -> participants.add(
                        EventParticipant.builder()
                                .event(event)
                                .participantType(ParticipantType.USER)
                                .user(user)
                                .build()
                ));

        petRepository.findAllById(petIds).stream()
                .filter(pet -> pet.getFamily() != null && familyId.equals(pet.getFamily().getId()))
                .forEach(pet -> participants.add(
                        EventParticipant.builder()
                                .event(event)
                                .participantType(ParticipantType.PET)
                                .pet(pet)
                                .build()
                ));

        familyMemberRepository.findAllById(memberIds).stream()
                .filter(m -> m.getFamily() != null && familyId.equals(m.getFamily().getId()))
                .forEach(member -> participants.add(
                        EventParticipant.builder()
                                .event(event)
                                .participantType(ParticipantType.FAMILY_MEMBER)
                                .familyMember(member)
                                .build()
                ));

        return participants;
    }

    private List<Long> extractUserIds(List<EventParticipant> participants) {
        return participants.stream()
                .filter(p -> p.getParticipantType() == ParticipantType.USER && p.getUser() != null)
                .map(p -> p.getUser().getId())
                .toList();
    }

    private List<Long> extractPetIds(List<EventParticipant> participants) {
        return participants.stream()
                .filter(p -> p.getParticipantType() == ParticipantType.PET && p.getPet() != null)
                .map(p -> p.getPet().getId())
                .toList();
    }

    private List<Long> extractFamilyMemberIds(List<EventParticipant> participants) {
        return participants.stream()
                .filter(p -> p.getParticipantType() == ParticipantType.FAMILY_MEMBER
                        && p.getFamilyMember() != null)
                .map(p -> p.getFamilyMember().getId())
                .toList();
    }

    // Collects all participant names into a single list — used for display in the events list
    private List<String> extractParticipantNames(List<EventParticipant> participants) {
        return participants.stream()
                .map(p -> {
                    if (p.getParticipantType() == ParticipantType.USER) {
                        return p.getUser() != null ? p.getUser().getDisplayName() : null;
                    } else if (p.getParticipantType() == ParticipantType.PET) {
                        return p.getPet() != null ? p.getPet().getName() : null;
                    } else if (p.getParticipantType() == ParticipantType.FAMILY_MEMBER) {
                        return p.getFamilyMember() != null ? p.getFamilyMember().getName() : null;
                    }
                    return null;
                })
                .filter(name -> name != null)
                .toList();
    }
}
