package com.familyhub.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        // Token'as perduodamas per formą (hidden laukas) — naudotojas jo nemato
        @NotBlank
        String token,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8–100 characters")
        String newPassword,

        // Patvirtinimas — tikrinamas service lygmenyje (ne anotacija,
        // nes Java record negali turėti cross-field validacijos anotacijų)
        @NotBlank(message = "Please confirm your password")
        String confirmPassword
) {}
