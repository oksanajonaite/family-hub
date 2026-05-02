package com.familyhub.dto.response.admin;

import com.familyhub.entity.enums.Role;

import java.time.LocalDateTime;
import java.util.List;

// View model for the admin dashboard. It exposes only the fields rendered by admin/index.html,
// so full User and Family entities do not need to travel into the view layer.
public record AdminDashboardData(
        long totalUsers,
        long totalFamilies,
        long usersWithoutFamily,
        long totalNotifications,
        List<AdminUserRow> users,
        List<AdminFamilyRow> families
) {
    public record AdminUserRow(
            Long id,
            String displayName,
            String email,
            Role role,
            String familyName,
            LocalDateTime createdAt
    ) {}

    public record AdminFamilyRow(
            Long id,
            String name,
            String createdByDisplayName,
            LocalDateTime createdAt
    ) {}
}
