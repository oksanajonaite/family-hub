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
    private final PasswordEncoder passwordEncoder; // Injektuojamas iš SecurityConfig @Bean

    // @Transactional — jei metodas mete exception, DB pakeitimai atšaukiami (rollback).
    // Čia: jei userRepository.save() nepavyktų — niekas neišsaugoma.
    @Transactional
    public User register(RegisterRequest request) {
        // Pirma patikriname ar email laisvas — kad netaupytumėme
        // brangaus BCrypt hash'avimo jei email jau užimtas
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                // passwordEncoder.encode() — paverčia "password123" į
                // "$2a$10$xyz..." (BCrypt hash). Originalo atkurti neįmanoma.
                .password(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                // Visi nauji vartotojai registruojasi kaip PARENT.
                // ADMIN sukuriamas tik per DB tiesiogiai — ne per formą.
                .role(Role.PARENT)
                .enabled(true)
                .build();

        // save() — INSERT į DB. Grąžina išsaugotą objektą su sugeneruotu id.
        return userRepository.save(user);
    }
}
