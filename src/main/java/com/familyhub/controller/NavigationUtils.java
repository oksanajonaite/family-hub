package com.familyhub.controller;

import org.springframework.ui.Model;

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
