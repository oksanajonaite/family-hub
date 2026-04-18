package com.familyhub.service;

import com.familyhub.dto.response.admin.AdminDashboardData;
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

    // Loads all admin dashboard data in a single @Transactional context.
    // Stats (totalUsers, totalFamilies, usersWithoutFamily) are derived from
    // the already-loaded lists — avoids redundant COUNT queries.
    @Transactional(readOnly = true)
    public AdminDashboardData getDashboardData() {
        List<User> users = userRepository.findAllByOrderByCreatedAtDesc();
        List<Family> families = familyRepository.findAllByOrderByCreatedAtAsc();

        long usersWithoutFamily = users.stream()
                .filter(u -> u.getFamily() == null && u.getRole() != Role.ADMIN)
                .count();

        return new AdminDashboardData(
                users.size(),
                families.size(),
                usersWithoutFamily,
                notificationRepository.count(),
                users,
                families
        );
    }
}
