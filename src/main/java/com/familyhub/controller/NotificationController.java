package com.familyhub.controller;

import com.familyhub.dto.response.notification.NotificationResponse;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // --- Pranešimų sąrašo puslapis ---
    // Model — Spring MVC mechanizmas duomenims perduoti į Thymeleaf šabloną.
    // @AuthenticationPrincipal — Spring Security automatiškai įdeda prisijungusį vartotoją.
    @GetMapping
    public String listNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        List<NotificationResponse> notifications = notificationService.getMyNotifications(currentUser);
        long unreadCount = notificationService.countUnread(currentUser);

        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", unreadCount);
        return "notifications/index";
    }

    // --- Vieno pranešimo pažymėjimas kaip perskaitytas ---
    // POST (ne GET) — nes keičiame duomenis. GET turi būti idempotentinis (nekeis duomenų).
    // RedirectAttributes — leidžia perduoti flash žinutę po redirect'o.
    // Flash atributo gyvavimo laikas — tik vienas sekantis request'as, po to ištrinamas.
    @PostMapping("/{id}/read")
    public String markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            notificationService.markAsRead(id, currentUser);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        // Grąžiname atgal į pranešimų sąrašą
        return "redirect:/notifications";
    }

    // --- Visų pranešimų pažymėjimas kaip perskaityti ---
    // Patogu kai pranešimų daug — vienu mygtuku išvalom badge'ą.
    @PostMapping("/read-all")
    public String markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        notificationService.markAllAsRead(currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "All notifications marked as read.");
        return "redirect:/notifications";
    }
}
