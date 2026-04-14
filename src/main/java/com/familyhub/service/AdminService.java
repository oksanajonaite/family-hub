package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.NotificationRepository;
import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public long getTotalUsers() {
        return userRepository.count();
    }

    @Transactional(readOnly = true)
    public long getTotalFamilies() {
        return familyRepository.count();
    }

    // Counts users who have registered but not yet joined a family.
    // ADMIN users are excluded — they are never part of a family by design.
    @Transactional(readOnly = true)
    public long getUsersWithoutFamily() {
        return userRepository.countByFamilyIsNullAndRoleNot(Role.ADMIN);
    }

    // Total notification count across the system — useful for monitoring notification activity
    @Transactional(readOnly = true)
    public long getTotalUnreadNotifications() {
        return notificationRepository.count();
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Family> getAllFamilies() {
        return familyRepository.findAll();
    }

    // Helper used by the admin template to display member count per family
    @Transactional(readOnly = true)
    public long getMemberCount(Long familyId) {
        return userRepository.findAllByFamilyId(familyId).size();
    }
}
