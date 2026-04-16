package com.familyhub.dto.response;

import com.familyhub.entity.Family;
import com.familyhub.entity.User;

import java.util.List;

// View model for the admin dashboard — bundles all data needed by admin/index.html.
// Avoids 6 separate model.addAttribute() calls in the controller.
public record AdminDashboardData(
        long totalUsers,
        long totalFamilies,
        long usersWithoutFamily,
        long totalNotifications,
        List<User> users,
        List<Family> families
) {}
