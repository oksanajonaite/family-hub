package com.familyhub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// unreadCount pridedamas automatiškai per GlobalModelAdvice —
// todėl čia nebereikia NotificationService ar Model
@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }
}
