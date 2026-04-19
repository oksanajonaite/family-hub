package com.familyhub.dto.request.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(

        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 100, message = "Display name must be 2–100 characters")
        String displayName,

        // Optional — user may clear the field or leave it blank
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth
) {}
