package com.familyhub.service;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyInvite;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.InvalidInviteCodeException;
import com.familyhub.exception.UserAlreadyInFamilyException;
import com.familyhub.repository.FamilyInviteRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FamilyInviteServiceTest {

    @Mock private FamilyRepository familyRepository;
    @Mock private FamilyInviteRepository familyInviteRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private FamilyInviteService familyInviteService;

    @Test
    void createFamily_whenUserAlreadyInFamily_throwsUserAlreadyInFamilyException() {
        User userWithFamily = User.builder()
                .id(1L)
                .email("parent@test.com")
                .family(Family.builder().id(10L).name("Existing Family").build())
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithFamily));

        assertThrows(UserAlreadyInFamilyException.class,
                () -> familyInviteService.createFamily(new CreateFamilyRequest("New Family"), 1L));

        verify(familyRepository, never()).save(any());
    }

    @Test
    void joinByInviteCode_whenCodeIsExpired_throwsInvalidInviteCodeException() {
        User newUser = User.builder()
                .id(2L)
                .email("new@test.com")
                .family(null)
                .build();

        FamilyInvite expiredInvite = FamilyInvite.builder()
                .code("EXPIREDCODE12")
                .family(Family.builder().id(10L).build())
                .role(Role.KID)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(newUser));
        when(familyInviteRepository.findByCode("EXPIREDCODE12")).thenReturn(Optional.of(expiredInvite));

        assertThrows(InvalidInviteCodeException.class,
                () -> familyInviteService.joinByInviteCode("EXPIREDCODE12", 2L));
    }

    @Test
    void joinByInviteCode_whenUserAlreadyInFamily_throwsUserAlreadyInFamilyException() {
        User userAlreadyInFamily = User.builder()
                .id(3L)
                .email("existing@test.com")
                .family(Family.builder().id(10L).build())
                .build();

        when(userRepository.findById(3L)).thenReturn(Optional.of(userAlreadyInFamily));

        assertThrows(UserAlreadyInFamilyException.class,
                () -> familyInviteService.joinByInviteCode("ANYCODE123", 3L));

        verify(familyInviteRepository, never()).findByCode(any());
    }
}
