package com.familyhub.controller;

import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

// Accessible only by users with the ADMIN role.
// Access is enforced in SecurityConfig: .requestMatchers("/admin/**").hasRole("ADMIN")
// Spring Security blocks all other roles automatically — no manual role check needed here.
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // GET /admin → redirect to /admin/dashboard
    @GetMapping
    public String adminRoot() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // Stats cards (summary numbers at the top)
        model.addAttribute("totalUsers", adminService.getTotalUsers());
        model.addAttribute("totalFamilies", adminService.getTotalFamilies());
        model.addAttribute("usersWithoutFamily", adminService.getUsersWithoutFamily());
        model.addAttribute("totalNotifications", adminService.getTotalUnreadNotifications());

        // Data tables (full lists below the cards)
        List<User> users = adminService.getAllUsers();
        List<Family> families = adminService.getAllFamilies();

        model.addAttribute("users", users);
        model.addAttribute("families", families);

        return "admin/index";
    }
}
