package com.familyhub.service;

import com.familyhub.dto.request.auth.ResetPasswordRequest;
import com.familyhub.entity.PasswordResetToken;
import com.familyhub.entity.User;
import com.familyhub.exception.InvalidTokenException;
import com.familyhub.repository.PasswordResetTokenRepository;
import com.familyhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė (OWASP): jei el. paštas neegzistuoja — nieko nedarome,
    // bet ir klaidos nerodome. Vartotojas visada mato tą pačią žinutę.
    // Priešingu atveju užpuolikas galėtų sužinoti kurie el. paštai yra registruoti.
    @Test
    void createResetToken_whenEmailNotFound_doesNothingQuietly() {
        // Arrange — el. paštas nerastas
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        // Act — metodas turi tyliai baigti darbą, be jokio exception
        assertDoesNotThrow(() -> passwordResetService.createResetToken("ghost@test.com"));

        // Assert — joks token'as nesukurtas, joks el. laiškas neišsiųstas
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordReset(any(), any());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: pasibaigęs token'as turi mesti InvalidTokenException.
    // Vartotojas turi gauti naują reset nuorodą — senoji nebegalioja.
    @Test
    void resetPassword_whenTokenIsExpired_throwsInvalidTokenException() {
        // Arrange
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .token("expired-token-uuid")
                .user(User.builder().id(1L).email("user@test.com").build())
                .expiresAt(LocalDateTime.now().minusHours(2)) // pasibaigė 2 valandas atgal
                .build();

        when(tokenRepository.findByToken("expired-token-uuid"))
                .thenReturn(Optional.of(expiredToken));

        ResetPasswordRequest request = new ResetPasswordRequest(
                "expired-token-uuid", "NewPass1!", "NewPass1!");

        // Act & Assert
        assertThrows(InvalidTokenException.class,
                () -> passwordResetService.resetPassword(request));
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // Švaros taisyklė: pasibaigęs token'as turi būti ištrintas.
    // Nereikalingi token'ai nesikaupia DB — juos valo ir Scheduler, bet ir čia.
    @Test
    void resetPassword_whenTokenIsExpired_deletesTheExpiredToken() {
        // Arrange
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .token("expired-token-uuid")
                .user(User.builder().id(1L).build())
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(tokenRepository.findByToken("expired-token-uuid"))
                .thenReturn(Optional.of(expiredToken));

        ResetPasswordRequest request = new ResetPasswordRequest(
                "expired-token-uuid", "NewPass1!", "NewPass1!");

        // Act
        assertThrows(InvalidTokenException.class,
                () -> passwordResetService.resetPassword(request));

        // Assert — pasibaigęs token'as turi būti ištrintas net ir exception atveju
        verify(tokenRepository).delete(expiredToken);
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: sėkmingai pakeitus slaptažodį, token'as turi būti ištrintas.
    // Token'as — vienkartinis. Pakartotinis naudojimas turi būti neįmanomas.
    // Taip pat: naujas slaptažodis turi būti koduojamas prieš išsaugant.
    @Test
    void resetPassword_whenTokenIsValid_savesEncodedPasswordAndDeletesToken() {
        // Arrange
        User user = User.builder().id(1L).email("user@test.com").password("old_hash").build();
        PasswordResetToken validToken = PasswordResetToken.builder()
                .token("valid-token-uuid")
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(30)) // dar galioja
                .build();

        when(tokenRepository.findByToken("valid-token-uuid"))
                .thenReturn(Optional.of(validToken));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("$2a$new_hash");

        ResetPasswordRequest request = new ResetPasswordRequest(
                "valid-token-uuid", "NewPass1!", "NewPass1!");

        // Act
        passwordResetService.resetPassword(request);

        // Assert — slaptažodis koduotas ir išsaugotas
        verify(passwordEncoder).encode("NewPass1!");
        verify(userRepository).save(user);
        assertEquals("$2a$new_hash", user.getPassword());

        // Token'as ištrintas po panaudojimo
        verify(tokenRepository).delete(validToken);
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // isTokenValid() grąžina false kai token'as pasibaigęs.
    // Naudojama prieš rodant reset formą — pasibaigęs token'as → klaidos puslapis.
    @Test
    void isTokenValid_whenTokenIsExpired_returnsFalse() {
        // Arrange
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .token("some-token")
                .expiresAt(LocalDateTime.now().minusSeconds(1)) // ką tik pasibaigė
                .build();

        when(tokenRepository.findByToken("some-token"))
                .thenReturn(Optional.of(expiredToken));

        // Act & Assert
        assertFalse(passwordResetService.isTokenValid("some-token"));
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────
    // isTokenValid() grąžina false kai token'as neegzistuoja.
    // Apsaugo nuo atakų su sugalvotais token'ais.
    @Test
    void isTokenValid_whenTokenNotFound_returnsFalse() {
        // Arrange
        when(tokenRepository.findByToken("fake-token")).thenReturn(Optional.empty());

        // Act & Assert
        assertFalse(passwordResetService.isTokenValid("fake-token"));
    }
}
