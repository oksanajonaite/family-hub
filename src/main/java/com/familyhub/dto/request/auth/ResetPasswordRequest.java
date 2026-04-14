package com.familyhub.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        // Token is passed via a hidden form field — the user never sees it
        @NotBlank
        String token,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8–100 characters")
        String newPassword,

        // Confirmation field — validated in the service layer (not via annotation,
        // because Java records cannot have cross-field validation annotations)
        @NotBlank(message = "Please confirm your password")
        String confirmPassword
) {}
