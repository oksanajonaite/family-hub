package com.familyhub.controller;

import com.familyhub.entity.enums.Role;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;

/**
 * Injects shared model attributes (current URI, unread notification count, today's date)
 * before every request handler in the listed family-scoped controllers.
 * AuthController is intentionally excluded — login/register pages do not need these attributes.
 * assignableTypes is used instead of a global @ControllerAdvice for explicit, predictable scope.
 */
@ControllerAdvice(assignableTypes = {
        DashboardController.class,
        TaskController.class,
        EventController.class,
        PetController.class,
        FamilyMemberController.class,
        FamilyController.class,
        NotificationController.class,
        AdminController.class,
        ReceiptController.class,
        SpendingController.class
})
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationService notificationService;

    // @ModelAttribute on a method — automatically adds attributes to the model
    // before every handler method in the listed controllers above.
    @ModelAttribute
    public void addGlobalAttributes(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            HttpServletRequest request,
            Model model
    ) {
        model.addAttribute("currentUri", request.getRequestURI());
        // Unread notification count — only for PARENT/KID users.
        // ADMIN has no family and no notifications, so skip it.
        if (currentUser != null && currentUser.getRole() != Role.ADMIN) {
            long unreadCount = notificationService.countUnread(currentUser.getId());
            model.addAttribute("unreadCount", unreadCount);
        }

        // Today's date as "yyyy-MM-dd" string — used as the max attribute on HTML date inputs
        // (e.g. date of birth cannot be in the future).
        model.addAttribute("today", LocalDate.now().toString());
    }
}
