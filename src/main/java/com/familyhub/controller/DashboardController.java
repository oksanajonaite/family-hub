package com.familyhub.controller;

import com.familyhub.entity.enums.Role;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public String dashboard(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) LocalDate selected,
            Model model
    ) {
        if (currentUser.getRole() == Role.ADMIN) {
            return "redirect:/admin";
        }

        model.addAttribute("currentDisplayName", currentUser.getDisplayName());
        model.addAttribute("cal", dashboardService.buildCalendarViewModel(year, month, selected, currentUser));
        return "dashboard";
    }
}
