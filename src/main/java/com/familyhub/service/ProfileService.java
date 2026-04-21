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
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final S3Service s3Service;

    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        user.setDisplayName(request.displayName());
        user.setDateOfBirth(request.dateOfBirth());
        user.setEmailNotificationsEnabled(request.emailNotificationsEnabled());

        return userRepository.save(user);
    }

    // Uploads a profile photo to S3, replaces the old one if it exists, and persists the S3 key.
    // The S3 key ("avatars/<uuid>.<ext>") is stored in DB — NOT a public URL.
    // Old photo key is passed directly to S3Service.deleteFile() before the new upload.
    @Transactional
    public void uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Delete the old photo from S3 before uploading the new one.
        // user.getAvatarUrl() holds the S3 key — S3Service.deleteFile accepts it directly.
        // Safe to call with null — returns immediately if no photo existed.
        s3Service.deleteFile(user.getAvatarUrl());

        String key = s3Service.uploadFile(file, "avatars");
        user.setAvatarUrl(key);
        userRepository.save(user);
    }

    // Returns the S3 key for the given user's avatar, or null if no photo uploaded.
    // Used by AvatarController to generate a pre-signed URL for the response.
    public String getAvatarKey(Long userId) {
        return userRepository.findById(userId)
                .map(User::getAvatarUrl)
                .orElse(null);
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
