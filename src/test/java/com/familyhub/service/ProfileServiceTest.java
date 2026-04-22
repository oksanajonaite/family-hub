package com.familyhub.service;

import com.familyhub.dto.request.profile.ChangePasswordRequest;
import com.familyhub.entity.User;
import com.familyhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private UserRepository userRepository;

    // PasswordEncoder yra interface — Mockito jį lengvai mock'ina.
    // Realus BCryptPasswordEncoder nekviečiamas — testai veikia greitai.
    @Mock private PasswordEncoder passwordEncoder;

    // S3Service reikalingas ProfileService konstruktoriui nuo AWS implementacijos.
    // changePassword() jo nenaudoja, bet @InjectMocks turi matyt visas priklausomybes.
    @Mock private S3Service s3Service;

    @InjectMocks
    private ProfileService profileService;

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: negalima keisti slaptažodžio nežinant dabartinio.
    // passwordEncoder.matches() grąžina false → neteisingas slaptažodis → exception.
    @Test
    void changePassword_whenCurrentPasswordIsWrong_throwsIllegalArgumentException() {
        // Arrange
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .password("$2a$hashed_correct_password") // BCrypt maiša realiame kode
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // passwordEncoder.matches("wrong", hash) → false — neteisingas slaptažodis
        when(passwordEncoder.matches("wrong_current", user.getPassword())).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "wrong_current",  // neteisingas dabartinis
                "NewPassword1!",
                "NewPassword1!"
        );

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> profileService.changePassword(1L, request));

        // Tikriname ir exception žinutę — ne tik tipą.
        // Taip garantuojame tinkamą UX pranešimą vartotojui.
        assert ex.getMessage().equals("Current password is incorrect.");

        // Slaptažodis NIEKADA neturėtų būti koduojamas jei validacija nepraėjo
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Verslo taisyklė: naujas slaptažodis turi skirtis nuo dabartinio.
    // Leidus keisti į tą patį slaptažodį — saugumo pagerėjimo nėra.
    @Test
    void changePassword_whenNewPasswordSameAsOld_throwsIllegalArgumentException() {
        // Arrange
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .password("$2a$hashed_password")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // 1-as patikrinimas praeina: dabartinis teisingas
        when(passwordEncoder.matches("SamePassword1!", user.getPassword())).thenReturn(true);

        // Naujas slaptažodis sutampa su senu — service turi atmesti
        // ProfileService tikrina: passwordEncoder.matches(newPassword, currentHash)
        // Kadangi naujas = senas, šis matches() taip pat grąžina true

        ChangePasswordRequest request = new ChangePasswordRequest(
                "SamePassword1!", // dabartinis — teisingas
                "SamePassword1!", // naujas — tas pats!
                "SamePassword1!"  // patvirtinimas
        );

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> profileService.changePassword(1L, request));

        assert ex.getMessage().equals("New password must be different from the current one.");
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // Validacija: naujas slaptažodis ir patvirtinimas turi sutapti.
    // Klaidingas patvirtinimas turėtų blokuoti keitimą.
    @Test
    void changePassword_whenConfirmPasswordDoesNotMatch_throwsIllegalArgumentException() {
        // Arrange
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .password("$2a$hashed_password")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Dabartinis slaptažodis teisingas
        when(passwordEncoder.matches("Current1!", user.getPassword())).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "Current1!",         // dabartinis — teisingas
                "NewPassword1!",     // naujas
                "DifferentPassword!" // patvirtinimas NESUTAMPA
        );

        // Act & Assert
        // ProfileService tikrina: !request.newPassword().equals(request.confirmPassword())
        // PRIEŠ tikrindamas ar naujas = senas — todėl šis exception metamas pirmas
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> profileService.changePassword(1L, request));

        assert ex.getMessage().equals("New passwords do not match.");

        // Slaptažodis NIEKADA neturėtų būti išsaugotas
        verify(userRepository, never()).save(any());
    }
}
