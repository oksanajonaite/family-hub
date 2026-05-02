package com.familyhub.controller;

import org.springframework.ui.Model;

/**
 * Utility for applying back-navigation attributes to the model.
 * When {@code from=dashboard}, sets back URL to /dashboard; otherwise uses the provided default.
 * Used by TaskController, EventController, and similar controllers that can be reached from multiple pages.
 */
class NavigationUtils {

    private NavigationUtils() {}

    static void applyBackNavigation(Model model, String from, String defaultUrl, String defaultLabel) {
        if ("dashboard".equals(from)) {
            model.addAttribute("backUrl", "/dashboard");
            model.addAttribute("backLabel", "Back to dashboard");
            model.addAttribute("fromDashboard", true);
        } else {
            model.addAttribute("backUrl", defaultUrl);
            model.addAttribute("backLabel", defaultLabel);
            model.addAttribute("fromDashboard", false);
        }
    }
}
