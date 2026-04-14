package com.familyhub.service;

import com.familyhub.dto.request.auth.RegisterRequest;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.UserAlreadyExistsException;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Injected from SecurityConfig @Bean

    // @Transactional — if an exception is thrown, all DB changes are rolled back.
    // Here: if userRepository.save() fails, nothing is persisted.
    @Transactional
    public User register(RegisterRequest request) {
        // Check email availability first — avoids running expensive BCrypt hashing
        // if the email is already taken
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                // passwordEncoder.encode() turns "password123" into a BCrypt hash
                // like "$2a$10$xyz...". The original cannot be recovered from the hash.
                .password(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                // Date of birth is optional — may be null if not provided
                .dateOfBirth(request.dateOfBirth())
                // All new users register as PARENT.
                // ADMIN role can only be assigned directly in the DB — not via the form.
                .role(Role.PARENT)
                .enabled(true)
                .build();

        // save() performs an INSERT and returns the persisted object with a generated id
        return userRepository.save(user);
    }
}
