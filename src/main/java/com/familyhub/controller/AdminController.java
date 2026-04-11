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

// Šis controller'is pasiekiamas TIK ADMIN rolės vartotojams.
// Apsauga nustatyta SecurityConfig: .requestMatchers("/admin/**").hasRole("ADMIN")
// Spring Security automatiškai blokuoja kitus — čia rolę tikrinti nebūtina.
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // GET /admin → nukreipia į /admin/dashboard
    @GetMapping
    public String adminRoot() {
        return "redirect:/admin/dashboard";
    }

    // --- Pagrindinis admin puslapis su statistika ---
    // Model — perduodame visus reikalingus duomenis į Thymeleaf šabloną
    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // Statistikos kortelės (skaičiai viršuje)
        model.addAttribute("totalUsers", adminService.getTotalUsers());
        model.addAttribute("totalFamilies", adminService.getTotalFamilies());
        model.addAttribute("usersWithoutFamily", adminService.getUsersWithoutFamily());
        model.addAttribute("totalNotifications", adminService.getTotalUnreadNotifications());

        // Lentelės (sąrašai apačioje)
        List<User> users = adminService.getAllUsers();
        List<Family> families = adminService.getAllFamilies();

        model.addAttribute("users", users);
        model.addAttribute("families", families);

        return "admin/index";
    }
}
