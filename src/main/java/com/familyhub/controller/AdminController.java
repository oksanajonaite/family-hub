package com.familyhub.controller;

import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.service.AdminService;
import com.familyhub.service.FamilyService;
import com.familyhub.service.ScheduledJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Accessible only by users with the ADMIN role.
// Access is enforced in SecurityConfig: .requestMatchers("/admin/**").hasRole("ADMIN")
// Spring Security blocks all other roles automatically — no manual role check needed here.
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final FamilyService familyService;
    private final ScheduledJobService scheduledJobService;

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

    @PostMapping("/families/{id}/delete")
    public String deleteFamily(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        try {
            familyService.deleteFamilyByAdmin(id);
            redirectAttributes.addFlashAttribute("successMessage", "Family deleted successfully.");
        } catch (FamilyNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    // ── Manual job triggers — dev convenience, admin-only ──────────────────────
    // These endpoints let the admin fire scheduled jobs on demand without waiting
    // for the cron timer. Useful for testing and for forcing a run in production.
    //
    // Each trigger is wrapped in try-catch so that a job failure (e.g. mail server
    // down, DB hiccup) shows a friendly error flash instead of a generic error page.

    @PostMapping("/jobs/birthday-reminders")
    public String triggerBirthdayReminders(RedirectAttributes redirectAttributes) {
        try {
            scheduledJobService.sendBirthdayReminders();
            redirectAttributes.addFlashAttribute("successMessage", "Birthday reminder job completed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Birthday reminder job failed: " + e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/jobs/event-reminders")
    public String triggerEventReminders(RedirectAttributes redirectAttributes) {
        try {
            scheduledJobService.sendEventReminders();
            redirectAttributes.addFlashAttribute("successMessage", "Event reminder job completed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Event reminder job failed: " + e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/jobs/cleanup-invites")
    public String triggerCleanupInvites(RedirectAttributes redirectAttributes) {
        try {
            scheduledJobService.cleanUpExpiredInviteCodes();
            redirectAttributes.addFlashAttribute("successMessage", "Expired invite codes cleaned up.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invite code cleanup failed: " + e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }
}
