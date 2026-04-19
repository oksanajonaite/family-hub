package com.familyhub.service;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyInvite;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.CannotRemoveMemberException;
import com.familyhub.exception.InvalidInviteCodeException;
import com.familyhub.exception.UserAlreadyInFamilyException;
import com.familyhub.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class) — JUnit 5 + Mockito integracija.
// Automatiškai inicializuoja @Mock ir @InjectMocks laukus prieš kiekvieną testą.
// Alternatyva: MockitoAnnotations.openMocks(this) setUp() metode — bet @ExtendWith yra švariau.
@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    // @Mock — Mockito sukuria apsimestinį (fake) objektą.
    // Jis niekada nekviečia realios DB — visi metodai grąžina null/empty pagal nutylėjimą,
    // arba tai ką nurodysi su when(...).thenReturn(...).
    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyInviteRepository familyInviteRepository;
    @Mock private UserRepository userRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private PetRepository petRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventParticipantRepository eventParticipantRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private NotificationRepository notificationRepository;

    // @InjectMocks — Mockito sukuria realų FamilyService objektą
    // ir automatiškai į jį įterpia visus @Mock laukus per konstruktorių.
    @InjectMocks
    private FamilyService familyService;

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Pavadinimo formatas: metodas_situacija_tikėtimasRezultatas
    // Čia testuojame verslo taisyklę: 1 vartotojas = 1 šeima.
    // Jei vartotojas jau priklauso šeimai — createFamily turi mesti exception.
    @Test
    void createFamily_whenUserAlreadyInFamily_throwsUserAlreadyInFamilyException() {
        // Arrange — paruošiame situaciją
        User userWithFamily = User.builder()
                .id(1L)
                .email("parent@test.com")
                .family(Family.builder().id(10L).name("Existing Family").build()) // jau šeimoje
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithFamily));

        // Act & Assert — kviečiame metodą ir tikriname kad mestų tinkamą exception
        assertThrows(UserAlreadyInFamilyException.class,
                () -> familyService.createFamily(new CreateFamilyRequest("New Family"), 1L));

        // Patikriname kad Family NIEKADA nebuvo išsaugotas — DB operacija neturėtų vykti
        verify(familyRepository, never()).save(any());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Verslo taisyklė: pasibaigęs invite kodas negalioja.
    // Prisijungti prie šeimos su senais kodais neturėtų būti įmanoma.
    @Test
    void joinByInviteCode_whenCodeIsExpired_throwsInvalidInviteCodeException() {
        // Arrange
        User newUser = User.builder()
                .id(2L)
                .email("new@test.com")
                .family(null) // vartotojas be šeimos — gali jungtis
                .build();

        FamilyInvite expiredInvite = FamilyInvite.builder()
                .code("EXPIREDCODE12")
                .family(Family.builder().id(10L).build())
                .role(Role.KID)
                .expiresAt(LocalDateTime.now().minusDays(1)) // pasibaigė vakar
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(newUser));
        when(familyInviteRepository.findByCode("EXPIREDCODE12")).thenReturn(Optional.of(expiredInvite));

        // Act & Assert
        // FamilyService naudoja .filter(i -> i.getExpiresAt().isAfter(now())) —
        // pasibaigęs kodas išfiltruojamas ir orElseThrow meta InvalidInviteCodeException
        assertThrows(InvalidInviteCodeException.class,
                () -> familyService.joinByInviteCode("EXPIREDCODE12", 2L));
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // Verslo taisyklė: vartotojas jau šeimoje negali prisijungti prie kitos.
    @Test
    void joinByInviteCode_whenUserAlreadyInFamily_throwsUserAlreadyInFamilyException() {
        // Arrange
        User userAlreadyInFamily = User.builder()
                .id(3L)
                .email("existing@test.com")
                .family(Family.builder().id(10L).build()) // jau turi šeimą
                .build();

        when(userRepository.findById(3L)).thenReturn(Optional.of(userAlreadyInFamily));

        // Act & Assert
        // Service patikrina family != null PRIEŠ ieškant kodo — todėl kodo mock'o nereikia
        assertThrows(UserAlreadyInFamilyException.class,
                () -> familyService.joinByInviteCode("ANYCODE123", 3L));

        // Invite kodas NIEKADA neturėtų būti ieškomas jei vartotojas jau šeimoje
        verify(familyInviteRepository, never()).findByCode(any());
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: PARENT negali pašalinti savęs iš šeimos.
    // Tai apsaugo nuo atsitiktinio šeimos palikimo.
    @Test
    void removeMember_whenRemovingSelf_throwsCannotRemoveMemberException() {
        // Arrange
        Long parentId = 1L;
        Family family = Family.builder().id(10L).build();

        User parent = User.builder()
                .id(parentId)
                .email("parent@test.com")
                .family(family)
                .build();

        when(userRepository.findById(parentId)).thenReturn(Optional.of(parent));

        // Act & Assert
        // memberId == requestingParentId → negali šalinti savęs
        assertThrows(CannotRemoveMemberException.class,
                () -> familyService.removeMember(parentId, parentId, 10L));
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: negalima pašalinti nario iš kitos šeimos.
    // Apsaugo nuo to, kad PARENT galėtų manipuliuoti kitų šeimų nariais.
    @Test
    void removeMember_whenMemberBelongsToDifferentFamily_throwsCannotRemoveMemberException() {
        // Arrange
        Long parentId = 1L;
        Long otherFamilyMemberId = 5L;

        // Narys priklauso šeimai ID=99, bet PARENT priklauso šeimai ID=10
        User memberFromOtherFamily = User.builder()
                .id(otherFamilyMemberId)
                .email("other@test.com")
                .family(Family.builder().id(99L).build()) // KITA šeima
                .build();

        when(userRepository.findById(otherFamilyMemberId)).thenReturn(Optional.of(memberFromOtherFamily));

        // Act & Assert
        // familyId=10, bet nario šeima=99 → neatitinka → exception
        assertThrows(CannotRemoveMemberException.class,
                () -> familyService.removeMember(otherFamilyMemberId, parentId, 10L));
    }
}
