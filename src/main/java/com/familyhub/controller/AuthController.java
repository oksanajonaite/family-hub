package com.familyhub.controller;

import com.familyhub.dto.request.auth.RegisterRequest;
import com.familyhub.exception.UserAlreadyExistsException;
import com.familyhub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest("", "", ""));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            authService.register(request);
            redirectAttributes.addFlashAttribute("successMessage", "Account created! Please log in.");
            return "redirect:/login";
        } catch (UserAlreadyExistsException e) {
            bindingResult.rejectValue("email", "error.email", "This email is already registered.");
            return "auth/register";
        }
    }
}
