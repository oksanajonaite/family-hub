package com.familyhub.controller;

import com.familyhub.dto.request.auth.ForgotPasswordRequest;
import com.familyhub.dto.request.auth.RegisterRequest;
import com.familyhub.dto.request.auth.ResetPasswordRequest;
import com.familyhub.exception.InvalidTokenException;
import com.familyhub.exception.UserAlreadyExistsException;
import com.familyhub.service.AuthService;
import com.familyhub.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles registration, login page, and password reset (forgot / reset flows).
 * Login POST is processed by Spring Security — no controller method is needed for it.
 * Methods return Thymeleaf view names, not JSON (@Controller, not @RestController).
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    // GET /login — renders the login form.
    // The POST for login is handled automatically by Spring Security — no controller method needed.
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login"; // → templates/auth/login.html
    }

    // GET /register — prepares an empty form for Thymeleaf binding.
    // Without the empty object, th:object="${registerRequest}" would throw an error.
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest("", "", "", null));
        return "auth/register";
    }

    // POST /register — processes the registration form
    @PostMapping("/register")
    public String register(
            // @Valid triggers Bean Validation annotations from RegisterRequest (@NotBlank, @Email, @Size, etc.)
            // BindingResult MUST immediately follow the @Valid parameter, otherwise Spring throws an exception
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            // RedirectAttributes passes data across the redirect (flash scope — survives exactly one request)
            RedirectAttributes redirectAttributes
    ) {
        // Validation errors (e.g. password too short) — re-render the form.
        // BindingResult errors are displayed automatically via Thymeleaf th:errors="*{field}".
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            authService.register(request);
            redirectAttributes.addFlashAttribute("successMessage", "Account created! Please log in.");
            return "redirect:/login";
        } catch (UserAlreadyExistsException e) {
            // rejectValue binds the error to the "email" field so Thymeleaf displays it inline
            bindingResult.rejectValue("email", "error.email", "This email is already registered.");
            return "auth/register";
        }
    }

    // --- Forgot password form ---
    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("forgotRequest", new ForgotPasswordRequest(""));
        return "auth/forgot-password";
    }

    // Always shows a success message — even if the email was not found.
    // Security reason: do not reveal whether an email exists in the system.
    // The reset token is printed to the IntelliJ console — copy and use the reset URL.
    @PostMapping("/forgot-password")
    public String forgotPassword(
            @Valid @ModelAttribute("forgotRequest") ForgotPasswordRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        passwordResetService.createResetToken(request.email());

        // Always show the same message — do not reveal whether the email exists
        redirectAttributes.addFlashAttribute("successMessage",
                "If this email exists, a reset link has been sent.");
        return "redirect:/forgot-password";
    }

    // @RequestParam — token arrives in the URL: /reset-password?token=abc123
    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam String token, Model model) {
        if (!passwordResetService.isTokenValid(token)) {
            model.addAttribute("errorMessage", "This reset link is invalid or has expired.");
            return "auth/reset-password-error";
        }

        model.addAttribute("resetRequest", new ResetPasswordRequest(token, "", ""));
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @Valid @ModelAttribute("resetRequest") ResetPasswordRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        if (!request.newPassword().equals(request.confirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirm", "Passwords do not match.");
            return "auth/reset-password";
        }

        try {
            passwordResetService.resetPassword(request);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Password changed successfully. Please log in.");
            return "redirect:/login";
        } catch (InvalidTokenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/forgot-password";
        }
    }
}
