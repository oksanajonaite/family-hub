package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.repository.EventParticipantRepository;
import com.familyhub.repository.EventRepository;
import com.familyhub.repository.FamilyInviteRepository;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.NotificationRepository;
import com.familyhub.repository.PetRepository;
import com.familyhub.repository.TaskRepository;
import com.familyhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyDeletionServiceTest {

    @Mock private FamilyRepository familyRepository;
    @Mock private UserRepository userRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private PetRepository petRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventParticipantRepository eventParticipantRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private FamilyInviteRepository familyInviteRepository;

    @InjectMocks
    private FamilyDeletionService familyDeletionService;

    @Test
    void deleteFamily_whenNameDoesNotMatch_throwsIllegalArgumentException() {
        Long familyId = 10L;
        Long parentId = 1L;
        Family family = Family.builder().id(familyId).name("Smith Family").build();

        User parent = User.builder()
                .id(parentId)
                .email("parent@test.com")
                .family(family)
                .build();

        when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
        when(userRepository.findById(parentId)).thenReturn(Optional.of(parent));

        assertThrows(IllegalArgumentException.class,
                () -> familyDeletionService.deleteFamily(familyId, parentId, "Wrong Name"));

        verify(familyRepository, never()).delete(any());
    }

    @Test
    void deleteFamily_whenRequesterBelongsToDifferentFamily_throwsForbiddenException() {
        Long targetFamilyId = 10L;
        Long attackerId = 2L;

        Family targetFamily = Family.builder().id(targetFamilyId).name("Target Family").build();

        User attackerFromOtherFamily = User.builder()
                .id(attackerId)
                .email("attacker@test.com")
                .family(Family.builder().id(99L).build())
                .build();

        when(familyRepository.findById(targetFamilyId)).thenReturn(Optional.of(targetFamily));
        when(userRepository.findById(attackerId)).thenReturn(Optional.of(attackerFromOtherFamily));

        assertThrows(ForbiddenException.class,
                () -> familyDeletionService.deleteFamily(targetFamilyId, attackerId, "Target Family"));

        verify(familyRepository, never()).delete(any());
    }
}
