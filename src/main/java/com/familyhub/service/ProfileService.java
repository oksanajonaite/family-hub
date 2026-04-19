package com.familyhub.service;

import com.familyhub.dto.request.profile.ChangePasswordRequest;
import com.familyhub.dto.request.profile.UpdateProfileRequest;
import com.familyhub.entity.User;
import com.familyhub.exception.UserNotFoundException;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setDisplayName(request.displayName());
        user.setDateOfBirth(request.dateOfBirth());
        user.setEmailNotificationsEnabled(request.emailNotificationsEnabled());

        return userRepository.save(user);
    }

    // Validates the current password before applying the change.
    // Throws IllegalArgumentException for business-rule violations so the
    // controller can catch them and show a user-friendly error message.
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("New passwords do not match.");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current one.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
