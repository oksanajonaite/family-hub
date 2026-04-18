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
    // totalUsers and totalFamilies are derived from the already-loaded lists (users.size(),
    // families.size()) — no extra COUNT queries needed because those lists are displayed
    // in the dashboard tables anyway. notificationRepository.count() is the one exception:
    // we only need the count, not the full list, so a single COUNT(*) query is correct here.
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
