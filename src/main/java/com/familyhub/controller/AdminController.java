package com.familyhub.controller;

import com.familyhub.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
        model.addAttribute("data", adminService.getDashboardData());
        return "admin/index";
    }
}
