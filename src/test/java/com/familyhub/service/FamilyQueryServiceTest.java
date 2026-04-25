package com.familyhub.service;

import com.familyhub.dto.response.family.FamilyPageData;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.Pet;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.UserNotFoundException;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.PetRepository;
import com.familyhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyQueryServiceTest {

    @Mock private FamilyRepository familyRepository;
    @Mock private UserRepository userRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private PetRepository petRepository;
    @Mock private FamilyInviteService familyInviteService;

    @InjectMocks
    private FamilyQueryService familyQueryService;

    // Pagrindinis saugumo testas: neegzistuojanti šeima turi mesti klaidą,
    // o ne grąžinti null, nes controller'is tuo pasikliautų be patikrinimo.
    @Test
    void getFamily_whenFamilyNotFound_throwsFamilyNotFoundException() {
        when(familyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(FamilyNotFoundException.class,
                () -> familyQueryService.getFamily(1L));
    }

    // Tikrina, kad buildFamilyPageData() greitai ir aiškiai meta klaidą,
    // jei currentUserId neegzistuoja DB — neleidžia vykdyti tolesnės logikos su null vartotoju.
    @Test
    void buildFamilyPageData_whenUserNotFound_throwsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> familyQueryService.buildFamilyPageData(10L, 99L));
    }

    // Integracinė patikra: buildFamilyPageData() turi surinkti duomenis iš visų 5 šaltinių
    // ir teisingai sudėti į FamilyPageData — teisinga šeima, nariai, gyvūnai, invite kodai.
    @Test
    void buildFamilyPageData_assemblesAllDataCorrectly() {
        Long familyId = 10L;
        Long currentUserId = 1L;

        Family family = Family.builder().id(familyId).name("Test Family").build();

        User currentUser = User.builder()
                .id(currentUserId)
                .email("parent@test.com")
                .family(family)
                .role(Role.PARENT)
                .dateOfBirth(LocalDate.of(1990, 5, 20))
                .emailNotificationsEnabled(true)
                .build();

        List<User> familyUsers = List.of(
                currentUser,
                User.builder().id(2L).email("kid@test.com").family(family).role(Role.KID).build()
        );
        List<FamilyMember> familyMembers = List.of(
                FamilyMember.builder().id(3L).name("Baby").family(family).build()
        );
        List<Pet> pets = List.of(
                Pet.builder().id(4L).name("Rex").family(family).build(),
                Pet.builder().id(5L).name("Luna").family(family).build()
        );

        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
        when(userRepository.findAllByFamilyId(familyId)).thenReturn(familyUsers);
        when(familyMemberRepository.findAllByFamilyId(familyId)).thenReturn(familyMembers);
        when(petRepository.findAllByFamilyId(familyId)).thenReturn(pets);
        when(familyInviteService.getActiveInviteCode(familyId, Role.PARENT)).thenReturn("PARENT123ABC");
        when(familyInviteService.getActiveInviteCode(familyId, Role.KID)).thenReturn("KID456DEF0");

        FamilyPageData result = familyQueryService.buildFamilyPageData(familyId, currentUserId);

        assertEquals(family, result.family());
        assertEquals(2, result.members().size());
        assertEquals(1, result.familyMembers().size());
        assertEquals(2, result.pets().size());
        assertEquals("PARENT123ABC", result.parentInviteCode());
        assertEquals("KID456DEF0", result.kidInviteCode());
        assertEquals(currentUserId, result.currentUserId());
        assertEquals(LocalDate.of(1990, 5, 20), result.currentUserDateOfBirth());
        assertTrue(result.currentUserEmailNotificationsEnabled());
    }
}
