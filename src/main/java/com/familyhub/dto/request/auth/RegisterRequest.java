package com.familyhub.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
        String password,

        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 100, message = "Display name must be 2-100 characters")
        String displayName,

        // Optional date of birth — @Past ensures the date is not in the future.
        // null is allowed — user can skip this field.
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth
) {}
