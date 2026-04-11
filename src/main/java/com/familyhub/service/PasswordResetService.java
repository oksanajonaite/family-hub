package com.familyhub.service;

import com.familyhub.dto.request.auth.ResetPasswordRequest;
import com.familyhub.entity.PasswordResetToken;
import com.familyhub.entity.User;
import com.familyhub.exception.InvalidTokenException;
import com.familyhub.repository.PasswordResetTokenRepository;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

// @Slf4j — Lombok generuoja: private static final Logger log = LoggerFactory.getLogger(...)
// Naudojame log.info() vietoje email siuntimo (kol nėra JavaMailSender)
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    // --- Token'o sukūrimas ---
    // Šiuo metu: token'as išvedamas į konsolę (IntelliJ Run log'ą).
    // Ateityje: vienintelis pakeitimas bus šiame metode —
    // vietoje log.info() kviesime emailService.sendResetEmail(user, token).
    @Transactional
    public void createResetToken(String email) {
        // Jei email nerastas — TYČIA nerodo klaidos vartotojui.
        // Saugumo priežastis: nenorime atskleisti ar email egzistuoja sistemoje.
        // Vartotojas visada mato tą patį sėkmės pranešimą.
        userRepository.findByEmail(email).ifPresent(user -> {

            // Ištriname senus token'us — vartotojas gali turėti tik vieną aktyvų
            tokenRepository.deleteByUserId(user.getId());

            // UUID — universaliai unikalus atsitiktinis identifikatorius.
            // Pvz.: "550e8400-e29b-41d4-a716-446655440000"
            // Praktiškai neįmanoma atspėti — 2^122 galimų reikšmių.
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    // Token'as galioja 1 valandą
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            tokenRepository.save(resetToken);

            // --- LAIKINAS: konsolės išvedimas vietoje email ---
            // Kai bus pridėtas JavaMailSender — ši eilutė bus pakeista email siuntimu.
            // Testavimo metu: nukopijuok token'ą iš IntelliJ konsolės ir įklijuok į URL.
            log.info("=================================================");
            log.info("PASSWORD RESET TOKEN for: {}", email);
            log.info("Token: {}", token);
            log.info("Use URL: http://localhost:8080/reset-password?token={}", token);
            log.info("=================================================");
        });
    }

    // --- Slaptažodžio keitimas ---
    // Tikrina: ar token'as egzistuoja, ar negaliojęs, ar slaptažodžiai sutampa.
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Slaptažodžių patvirtinimo tikrinimas — cross-field validacija
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        // Token'o paieška DB — jei nerastas, metame InvalidTokenException
        PasswordResetToken resetToken = tokenRepository.findByToken(request.token())
                .orElseThrow(InvalidTokenException::new);

        // Galiojimo patikrinimas — token'as turi būti dar nepraėjęs
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Pasibaigusį token'ą ištriname — nebereikalingas
            tokenRepository.delete(resetToken);
            throw new InvalidTokenException();
        }

        // Atnaujiname slaptažodį — encode() paverčia tekstą BCrypt hash'u
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Token'as sunaudotas — ištriname kad nebūtų panaudotas antrą kartą
        tokenRepository.delete(resetToken);

        log.info("Password successfully reset for: {}", user.getEmail());
    }

    // --- Token'o validacija (GET formoje) ---
    // Patikrina ar token'as egzistuoja ir dar galioja — prieš rodant reset formą.
    // Grąžina true jei galioja, false jei ne.
    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return tokenRepository.findByToken(token)
                .map(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }
}
