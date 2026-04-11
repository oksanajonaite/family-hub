package com.familyhub.service;

import com.familyhub.dto.request.event.CreateEventRequest;
import com.familyhub.dto.request.event.UpdateEventRequest;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.Event;
import com.familyhub.entity.EventParticipant;
import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.ParticipantType;
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

import java.util.ArrayList;
import java.util.List;

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

    @Transactional(readOnly = true)
    public List<EventResponse> getVisibleFamilyEvents(Long familyId, CustomUserDetails currentUser) {
        List<Event> allEvents = eventRepository.findAllByFamilyIdOrderByStartsAtAsc(familyId);

        return allEvents.stream()
                .filter(event -> !event.isPrivateEvent()
                        || event.getCreatedBy().getId().equals(currentUser.getId()))
                .map(event -> {
                    List<EventParticipant> participants = eventParticipantRepository.findAllByEventId(event.getId());
                    return eventMapper.toResponse(
                            event,
                            extractUserIds(participants),
                            extractPetIds(participants),
                            extractFamilyMemberIds(participants)
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Long eventId, CustomUserDetails currentUser) {
        Event event = getEventBelongingToFamily(eventId, currentUser.getFamilyId());

        if (event.isPrivateEvent() && !event.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new EventNotFoundException(eventId);
        }

        List<EventParticipant> participants = eventParticipantRepository.findAllByEventId(eventId);
        return eventMapper.toResponse(
                event,
                extractUserIds(participants),
                extractPetIds(participants),
                extractFamilyMemberIds(participants)
        );
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

        Event saved = eventRepository.save(event);

        List<EventParticipant> participants = buildParticipants(
                saved,
                request.participantUserIds(),
                request.participantPetIds(),
                request.participantFamilyMemberIds(),
                currentUser.getFamilyId()
        );
        eventParticipantRepository.saveAll(participants);

        return eventMapper.toResponse(
                saved,
                extractUserIds(participants),
                extractPetIds(participants),
                extractFamilyMemberIds(participants)
        );
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
        Event saved = eventRepository.save(event);

        eventParticipantRepository.deleteAllByEventId(eventId);
        List<EventParticipant> participants = buildParticipants(
                saved,
                request.participantUserIds(),
                request.participantPetIds(),
                request.participantFamilyMemberIds(),
                currentUser.getFamilyId()
        );
        eventParticipantRepository.saveAll(participants);

        return eventMapper.toResponse(
                saved,
                extractUserIds(participants),
                extractPetIds(participants),
                extractFamilyMemberIds(participants)
        );
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

    // --- Pagalbinis metodas: evento paieška su šeimos patikrinimu ---
    private Event getEventBelongingToFamily(Long eventId, Long familyId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (!event.getFamily().getId().equals(familyId)) {
            throw new EventNotFoundException(eventId);
        }
        return event;
    }

    // --- Dalyvių sąrašo kūrimas ---
    // Tikriname ar kiekvienas nurodytas dalyvis priklauso šiai šeimai.
    // Jei ne — praleidžiame (UI rodo tik šeimos narius, tai neturėtų įvykti).
    private List<EventParticipant> buildParticipants(
            Event event,
            List<Long> userIds,
            List<Long> petIds,
            List<Long> familyMemberIds,
            Long familyId
    ) {
        List<EventParticipant> participants = new ArrayList<>();

        if (userIds != null) {
            for (Long userId : userIds) {
                userRepository.findById(userId)
                        .filter(u -> u.getFamily() != null && familyId.equals(u.getFamily().getId()))
                        .ifPresent(user -> participants.add(
                                EventParticipant.builder()
                                        .event(event)
                                        .participantType(ParticipantType.USER)
                                        .user(user)
                                        .build()
                        ));
            }
        }

        if (petIds != null) {
            for (Long petId : petIds) {
                petRepository.findById(petId)
                        .filter(pet -> pet.getFamily() != null && familyId.equals(pet.getFamily().getId()))
                        .ifPresent(pet -> participants.add(
                                EventParticipant.builder()
                                        .event(event)
                                        .participantType(ParticipantType.PET)
                                        .pet(pet)
                                        .build()
                        ));
            }
        }

        // Šeimos nariai be paskyros (pvz. maži vaikai)
        if (familyMemberIds != null) {
            for (Long memberId : familyMemberIds) {
                familyMemberRepository.findById(memberId)
                        .filter(m -> m.getFamily() != null && familyId.equals(m.getFamily().getId()))
                        .ifPresent(member -> participants.add(
                                EventParticipant.builder()
                                        .event(event)
                                        .participantType(ParticipantType.FAMILY_MEMBER)
                                        .familyMember(member)
                                        .build()
                        ));
            }
        }

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
}
