package com.familyhub.controller;

import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final NotificationService notificationService;

    @GetMapping("/dashboard")
    public String dashboard(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        // Neperskaitytų pranešimų skaičius — rodomas kaip badge mygtuke
        long unreadCount = notificationService.countUnread(currentUser);
        model.addAttribute("unreadCount", unreadCount);
        return "dashboard";
    }
}
