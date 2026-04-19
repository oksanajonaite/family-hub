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
        LocalDate dateOfBirth,

        // HTML checkbox behaviour: when a checkbox is unchecked, the browser does NOT include
        // that field in the POST request at all — it simply disappears from the form data.
        // Spring MVC sees a missing boolean parameter and binds it as false automatically.
        // This means: checked → "emailNotificationsEnabled=true" arrives → bound as true.
        //             unchecked → field absent from request → Spring binds as false.
        // No extra logic needed — the default Java boolean binding handles opt-out correctly.
        boolean emailNotificationsEnabled
) {}
