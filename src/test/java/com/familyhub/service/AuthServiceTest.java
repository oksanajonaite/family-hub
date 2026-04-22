package com.familyhub.service;

import com.familyhub.dto.request.auth.RegisterRequest;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.UserAlreadyExistsException;
import com.familyhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Verslo taisyklė: du vartotojai negali turėti to paties el. pašto.
    // Svarbu: BCrypt hashing NETURĖTŲ būti kviestas jei el. paštas jau užimtas —
    // tai brangi operacija ir tuščia jei registracija vis tiek nepavyks.
    @Test
    void register_whenEmailAlreadyExists_throwsUserAlreadyExistsException() {
        // Arrange
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest(
                "taken@test.com", "Password1!", "Jonas", null);

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class,
                () -> authService.register(request));

        // BCrypt hashing neturėtų būti kviestas — brangus ir nereikalingas
        verify(passwordEncoder, never()).encode(any());
        // Vartotojas NIEKADA neturėtų būti išsaugotas
        verify(userRepository, never()).save(any());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: slaptažodis turi būti koduojamas prieš išsaugant.
    // Jei kažkada kiltų DB nutekėjimas — originalūs slaptažodžiai liktų apsaugoti.
    // Taip pat tikriname: naujas vartotojas visada gauna PARENT rolę.
    @Test
    void register_whenEmailIsNew_savesUserWithEncodedPasswordAndParentRole() {
        // Arrange
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("$2a$hashed");

        User savedUser = User.builder()
                .id(1L)
                .email("new@test.com")
                .password("$2a$hashed")
                .displayName("Jonas")
                .role(Role.PARENT)
                .enabled(true)
                .build();
        when(userRepository.save(any())).thenReturn(savedUser);

        RegisterRequest request = new RegisterRequest(
                "new@test.com", "Password1!", "Jonas", null);

        // Act
        User result = authService.register(request);

        // Assert — slaptažodis koduotas, rolė PARENT
        verify(passwordEncoder).encode("Password1!");
        verify(userRepository).save(any());
        assertEquals(Role.PARENT, result.getRole());
        assertEquals("$2a$hashed", result.getPassword());
    }
}
