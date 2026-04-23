package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.exception.CannotRemoveMemberException;
import com.familyhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyMembershipServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private FamilyMembershipService familyMembershipService;

    @Test
    void removeMember_whenRemovingSelf_throwsCannotRemoveMemberException() {
        Long parentId = 1L;
        Family family = Family.builder().id(10L).build();

        User parent = User.builder()
                .id(parentId)
                .email("parent@test.com")
                .family(family)
                .build();

        when(userRepository.findById(parentId)).thenReturn(Optional.of(parent));

        assertThrows(CannotRemoveMemberException.class,
                () -> familyMembershipService.removeMember(parentId, parentId, 10L));
    }

    @Test
    void removeMember_whenMemberBelongsToDifferentFamily_throwsCannotRemoveMemberException() {
        Long parentId = 1L;
        Long otherFamilyMemberId = 5L;

        User memberFromOtherFamily = User.builder()
                .id(otherFamilyMemberId)
                .email("other@test.com")
                .family(Family.builder().id(99L).build())
                .build();

        when(userRepository.findById(otherFamilyMemberId)).thenReturn(Optional.of(memberFromOtherFamily));

        assertThrows(CannotRemoveMemberException.class,
                () -> familyMembershipService.removeMember(otherFamilyMemberId, parentId, 10L));
    }
}
