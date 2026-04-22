package com.familyhub.service;

import com.familyhub.entity.Event;
import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.RecurrenceType;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.EventNotFoundException;
import com.familyhub.exception.ForbiddenException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    // EventService turi daug priklausomybių — visos turi būti @Mock
    // kad @InjectMocks galėtų sukurti EventService per konstruktorių.
    // Nenaudojami mock'ai tiesiog nieko negrąžins (null/empty) — tai normalu.
    @Mock private EventRepository eventRepository;
    @Mock private EventParticipantRepository eventParticipantRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PetRepository petRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private EventMapper eventMapper;

    @InjectMocks
    private EventService eventService;

    // Pagalbinis metodas — sukuria CustomUserDetails su nurodytu ID ir šeimos ID.
    // Naudojamas visuose EventService testuose kuriems reikia autentifikuoto vartotojo.
    private CustomUserDetails buildUser(Long userId, Long familyId, Role role) {
        User user = User.builder()
                .id(userId)
                .email("user@test.com")
                .displayName("Test User")
                .password("hashed")
                .role(role)
                .family(Family.builder().id(familyId).build())
                .enabled(true)
                .build();
        return new CustomUserDetails(user);
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: privatus renginys matomas TIK jo kūrėjui.
    // Kitas šeimos narys neturėtų matyti net jo egzistavimo —
    // todėl EventService meta EventNotFoundException (o ne ForbiddenException).
    // Tai yra "security through obscurity" principas: neatskleisti ar renginys egzistuoja.
    @Test
    void getEventById_whenEventIsPrivateAndUserIsNotCreator_throwsEventNotFoundException() {
        // Arrange
        Long familyId = 10L;
        Long creatorId = 1L;
        Long otherUserId = 2L; // kitas vartotojas — ne kūrėjas

        // Kūrėjas — renginio savininkas
        User creator = User.builder().id(creatorId).build();

        Family family = Family.builder().id(familyId).build();

        // Privatus renginys, kurį sukūrė kūrėjas (ID=1)
        Event privateEvent = Event.builder()
                .id(100L)
                .title("Secret meeting")
                .family(family)
                .createdBy(creator)
                .privateEvent(true) // privatus!
                .startsAt(LocalDateTime.now().plusDays(1))
                .recurrenceType(RecurrenceType.NONE)
                .build();

        when(eventRepository.findById(100L)).thenReturn(Optional.of(privateEvent));

        // Kitas vartotojas (ID=2) bando peržiūrėti privatų renginį
        CustomUserDetails otherUser = buildUser(otherUserId, familyId, Role.KID);

        // Act & Assert
        // EventService: if (event.isPrivateEvent() && !event.getCreatedBy().getId().equals(currentUser.getId()))
        //     throw new EventNotFoundException(eventId);
        assertThrows(EventNotFoundException.class,
                () -> eventService.getEventById(100L, otherUser));
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Teigiamas testas: privatus renginys MATOMAS jo kūrėjui.
    // Patvirtina kad apsauga neblokuoja teisėto savininko prieigos.
    @Test
    void getEventById_whenEventIsPrivateAndUserIsCreator_doesNotThrow() {
        // Arrange
        Long familyId = 10L;
        Long creatorId = 1L;

        User creator = User.builder().id(creatorId).build();
        Family family = Family.builder().id(familyId).build();

        Event privateEvent = Event.builder()
                .id(100L)
                .title("Secret meeting")
                .family(family)
                .createdBy(creator)
                .privateEvent(true)
                .startsAt(LocalDateTime.now().plusDays(1))
                .recurrenceType(RecurrenceType.NONE)
                .build();

        when(eventRepository.findById(100L)).thenReturn(Optional.of(privateEvent));
        // eventParticipantRepository.findAllByEventId() bus kviestas toEventResponse() —
        // mock'as grąžins tuščią sąrašą pagal nutylėjimą, kas yra priimtina šiam testui
        when(eventParticipantRepository.findAllByEventId(100L)).thenReturn(java.util.List.of());

        // Kūrėjas žiūri į savo renginį — tai LEIDŽIAMA
        CustomUserDetails creatorDetails = buildUser(creatorId, familyId, Role.PARENT);

        // Act & Assert — jokio exception
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> eventService.getEventById(100L, creatorDetails));
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: negalima peržiūrėti kitos šeimos renginio.
    // Svarbu: EventService meta EventNotFoundException (NE ForbiddenException) —
    // tai tyčinis dizainas. Neatskleisti ar renginys apskritai egzistuoja kitoje šeimoje.
    // Jei mestum ForbiddenException, vartotojas žinotų "renginys egzistuoja, bet nematomas".
    // EventNotFoundException sako "tokio renginio nėra" — saugiau.
    @Test
    void getEventById_whenEventBelongsToDifferentFamily_throwsEventNotFoundException() {
        // Arrange
        Long myFamilyId = 10L;
        Long otherFamilyId = 99L; // kita šeima

        User creator = User.builder().id(1L).build();

        // Renginys priklauso kitai šeimai (ID=99)
        Event eventFromOtherFamily = Event.builder()
                .id(200L)
                .title("Other family event")
                .family(Family.builder().id(otherFamilyId).build()) // KITA šeima
                .createdBy(creator)
                .privateEvent(false)
                .startsAt(LocalDateTime.now().plusDays(1))
                .recurrenceType(RecurrenceType.NONE)
                .build();

        when(eventRepository.findById(200L)).thenReturn(Optional.of(eventFromOtherFamily));

        // Vartotojas priklauso myFamilyId=10
        CustomUserDetails userFromMyFamily = buildUser(2L, myFamilyId, Role.PARENT);

        // Act & Assert
        // getEventBelongingToFamily: event.getFamily().getId() (99) != familyId (10)
        // → EventNotFoundException — ne ForbiddenException (žr. komentarą viršuje)
        assertThrows(EventNotFoundException.class,
                () -> eventService.getEventById(200L, userFromMyFamily));
    }

    // ── combineDateTime helpers ───────────────────────────────────────────────
    // Šie metodai pažymėti package-private specialiai testams (žr. EventService komentarą).
    // Testuojame juos tiesiogiai — tai tiksliau nei tikrinti per createEvent().

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Kai laikas nenurodytas (null) — turi grąžinti vidurnaktį.
    // Svarbu: renginys vis tiek turi atsirasti teisingą dieną,
    // net jei vartotojas nepasirinko konkrečios valandos.
    @Test
    void combineDateTime_whenTimeIsNull_returnsMidnight() {
        LocalDate date = LocalDate.of(2025, 6, 15);

        LocalDateTime result = eventService.combineDateTime(date, null);

        assertEquals(LocalDateTime.of(2025, 6, 15, 0, 0), result);
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // Kai laikas nurodytas — turi teisingai sujungti datą ir laiką.
    @Test
    void combineDateTime_whenTimeIsProvided_combinesCorrectly() {
        LocalDate date = LocalDate.of(2025, 6, 15);
        LocalTime time = LocalTime.of(14, 30);

        LocalDateTime result = eventService.combineDateTime(date, time);

        assertEquals(LocalDateTime.of(2025, 6, 15, 14, 30), result);
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────
    // Pabaigos data neprivaloma — kai null, turi grąžinti null.
    // Renginiai be pabaigos laiko yra leistini (pvz. "visą dieną").
    @Test
    void buildEndsAt_whenEndDateIsNull_returnsNull() {
        LocalTime time = LocalTime.of(18, 0);

        LocalDateTime result = eventService.buildEndsAt(null, time);

        assertNull(result);
    }

    // ── Test 7 ────────────────────────────────────────────────────────────────
    // Kai pabaigos data nurodyta, bet laikas ne — turi grąžinti vidurnaktį tos dienos.
    @Test
    void buildEndsAt_whenEndDateProvidedButTimeIsNull_returnsMidnight() {
        LocalDate endDate = LocalDate.of(2025, 6, 15);

        LocalDateTime result = eventService.buildEndsAt(endDate, null);

        assertEquals(LocalDateTime.of(2025, 6, 15, 0, 0), result);
    }
}
