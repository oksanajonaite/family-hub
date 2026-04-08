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
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(request.email());
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .role(Role.PARENT)
                .enabled(true)
                .build();

        return userRepository.save(user);
    }
}
