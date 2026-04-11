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
    private final EventMapper eventMapper;

    // --- Visų šeimos eventų gavimas ---
    // Privatūs eventai filtruojami: matomi tik jų kūrėjui.
    @Transactional(readOnly = true)
    public List<EventResponse> getVisibleFamilyEvents(Long familyId, CustomUserDetails currentUser) {
        List<Event> allEvents = eventRepository.findAllByFamilyIdOrderByStartsAtAsc(familyId);

        return allEvents.stream()
                // Rodome eventą jei jis nėra privatus ARBA jei jį sukūrė dabartinis naudotojas
                .filter(event -> !event.isPrivateEvent()
                        || event.getCreatedBy().getId().equals(currentUser.getId()))
                .map(event -> {
                    List<EventParticipant> participants = eventParticipantRepository.findAllByEventId(event.getId());
                    return eventMapper.toResponse(event, extractUserIds(participants), extractPetIds(participants));
                })
                .toList();
    }

    // --- Vieno evento gavimas su matomumo patikrinimu ---
    @Transactional(readOnly = true)
    public EventResponse getEventById(Long eventId, CustomUserDetails currentUser) {
        Event event = getEventBelongingToFamily(eventId, currentUser.getFamilyId());

        // Privatus eventas — tik kūrėjas gali jį matyti
        if (event.isPrivateEvent() && !event.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new EventNotFoundException(eventId);
        }

        List<EventParticipant> participants = eventParticipantRepository.findAllByEventId(eventId);
        return eventMapper.toResponse(event, extractUserIds(participants), extractPetIds(participants));
    }

    // --- Evento sukūrimas ---
    // Visi šeimos nariai gali kurti eventus.
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, CustomUserDetails currentUser) {
        // Gauname Family ir User entity nuorodas — reikalingos Event entity laukams
        Family family = familyRepository.findById(currentUser.getFamilyId())
                .orElseThrow(() -> new IllegalStateException("Family not found for current user"));

        User createdBy = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Mapper konvertuoja request → entity (be family, createdBy — juos užpildom rankiniu būdu)
        Event event = eventMapper.toEntity(request);
        event.setFamily(family);
        event.setCreatedBy(createdBy);

        Event saved = eventRepository.save(event);

        // Išsaugome dalyvius atskiroje lentelėje event_participants
        List<EventParticipant> participants = buildParticipants(
                saved, request.participantUserIds(), request.participantPetIds(), currentUser.getFamilyId()
        );
        eventParticipantRepository.saveAll(participants);

        return eventMapper.toResponse(saved, extractUserIds(participants), extractPetIds(participants));
    }

    // --- Evento atnaujinimas ---
    // Redaguoti gali: PARENT arba evento kūrėjas.
    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request, CustomUserDetails currentUser) {
        Event event = getEventBelongingToFamily(eventId, currentUser.getFamilyId());

        boolean isCreator = event.getCreatedBy().getId().equals(currentUser.getId());
        boolean isParent = currentUser.getRole() == Role.PARENT;

        if (!isCreator && !isParent) {
            throw new AccessDeniedException();
        }

        // Mapper atnaujina esamą entity laukus iš request (null laukai ignoruojami)
        eventMapper.updateEntity(request, event);
        Event saved = eventRepository.save(event);

        // Pakeičiame dalyvių sąrašą: ištriname senus, įrašome naujus
        eventParticipantRepository.deleteAllByEventId(eventId);
        List<EventParticipant> participants = buildParticipants(
                saved, request.participantUserIds(), request.participantPetIds(), currentUser.getFamilyId()
        );
        eventParticipantRepository.saveAll(participants);

        return eventMapper.toResponse(saved, extractUserIds(participants), extractPetIds(participants));
    }

    // --- Evento ištrynimas ---
    // Ištrinti gali: PARENT arba evento kūrėjas.
    @Transactional
    public void deleteEvent(Long eventId, CustomUserDetails currentUser) {
        Event event = getEventBelongingToFamily(eventId, currentUser.getFamilyId());

        boolean isCreator = event.getCreatedBy().getId().equals(currentUser.getId());
        boolean isParent = currentUser.getRole() == Role.PARENT;

        if (!isCreator && !isParent) {
            throw new AccessDeniedException();
        }

        // Pirma ištriname dalyvius (FK apribojimas), tada patį eventą
        eventParticipantRepository.deleteAllByEventId(eventId);
        eventRepository.delete(event);
    }

    // --- Pagalbinis metodas: evento paieška su šeimos patikrinimu ---
    // Apsauga nuo URL manipulation: naudotojas negali pasiekti kitos šeimos eventų.
    // Jei eventas neegzistuoja arba priklauso kitai šeimai — metame EventNotFoundException.
    private Event getEventBelongingToFamily(Long eventId, Long familyId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (!event.getFamily().getId().equals(familyId)) {
            throw new EventNotFoundException(eventId);
        }

        return event;
    }

    // --- Pagalbinis metodas: dalyvių sąrašo kūrimas ---
    // Tikriname ar visi nurodyti naudotojai ir gyvūnai priklauso šiai šeimai.
    // Jei ne — tiesiog praleidžiame (ne klaida, nes UI rodo tik šeimos narius).
    private List<EventParticipant> buildParticipants(
            Event event, List<Long> userIds, List<Long> petIds, Long familyId
    ) {
        List<EventParticipant> participants = new ArrayList<>();

        if (userIds != null) {
            for (Long userId : userIds) {
                // Pridedame naudotoją tik jei jis priklauso šiai šeimai
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
                // Pridedame gyvūną tik jei jis priklauso šiai šeimai
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

        return participants;
    }

    // Ištraukia USER tipo dalyvių id sąrašą
    private List<Long> extractUserIds(List<EventParticipant> participants) {
        return participants.stream()
                .filter(p -> p.getParticipantType() == ParticipantType.USER && p.getUser() != null)
                .map(p -> p.getUser().getId())
                .toList();
    }

    // Ištraukia PET tipo dalyvių id sąrašą
    private List<Long> extractPetIds(List<EventParticipant> participants) {
        return participants.stream()
                .filter(p -> p.getParticipantType() == ParticipantType.PET && p.getPet() != null)
                .map(p -> p.getPet().getId())
                .toList();
    }
}
