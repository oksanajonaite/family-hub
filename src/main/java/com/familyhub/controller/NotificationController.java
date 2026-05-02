package com.familyhub.controller;

import com.familyhub.dto.response.notification.NotificationResponse;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.NotificationNotFoundException;
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

/**
 * Lists and manages notifications for the currently logged-in user.
 * Supports marking individual or all notifications as read, which clears the navbar badge.
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // @AuthenticationPrincipal — Spring Security injects the currently logged-in user automatically
    @GetMapping
    public String listNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        List<NotificationResponse> notifications = notificationService.getMyNotifications(currentUser.getId());
        long unreadCount = notifications.stream().filter(n -> !n.read()).count();

        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", unreadCount);
        return "notifications/index";
    }

    // POST — not GET, because this modifies data. GET requests must be idempotent (read-only).
    @PostMapping("/{id}/read")
    public String markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            notificationService.markAsRead(id, currentUser.getId());
        } catch (NotificationNotFoundException | ForbiddenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/notifications";
    }

    // Marks all notifications as read at once — clears the navbar badge in one click
    @PostMapping("/read-all")
    public String markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        notificationService.markAllAsRead(currentUser.getId());
        redirectAttributes.addFlashAttribute("successMessage", "All notifications marked as read.");
        return "redirect:/notifications";
    }
}
