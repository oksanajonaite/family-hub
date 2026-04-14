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

// @Slf4j — Lombok generates: private static final Logger log = LoggerFactory.getLogger(...)
// Used for log.info() output instead of actual email sending (until JavaMailSender is added)
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    // Currently: the token is printed to the console (IntelliJ Run log).
    // Future: the only change needed here is replacing log.info() with
    // emailService.sendResetEmail(user, token) once JavaMailSender is added.
    @Transactional
    public void createResetToken(String email) {
        // If email is not found, intentionally do NOT show an error to the user.
        // Security reason: we don't want to reveal whether an email exists in the system.
        // The user always sees the same success message regardless.
        userRepository.findByEmail(email).ifPresent(user -> {

            // Delete any existing tokens — a user should have only one active reset token
            tokenRepository.deleteByUserId(user.getId());

            // UUID provides a cryptographically random, practically unguessable token (2^122 possibilities)
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    // Token is valid for 1 hour
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            tokenRepository.save(resetToken);

            // TEMPORARY: console output instead of email.
            // Once JavaMailSender is added, replace these log lines with an email call.
            // During testing: copy the token from the IntelliJ console and paste it into the URL.
            log.info("=================================================");
            log.info("PASSWORD RESET TOKEN for: {}", email);
            log.info("Token: {}", token);
            log.info("Use URL: http://localhost:8080/reset-password?token={}", token);
            log.info("=================================================");
        });
    }

    // Validates token existence and expiry, then updates the password.
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Cross-field validation: confirm that both password fields match
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(request.token())
                .orElseThrow(InvalidTokenException::new);

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            // Delete the expired token before throwing — no longer needed
            tokenRepository.delete(resetToken);
            throw new InvalidTokenException();
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Delete the token after use so it cannot be reused
        tokenRepository.delete(resetToken);

        log.info("Password successfully reset for: {}", user.getEmail());
    }

    // Checks whether a token exists and has not yet expired — called before showing the reset form.
    // Returns true if valid, false otherwise.
    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {
        return tokenRepository.findByToken(token)
                .map(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }
}
