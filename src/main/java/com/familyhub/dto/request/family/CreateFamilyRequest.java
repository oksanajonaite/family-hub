package com.familyhub.dto.request.family;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFamilyRequest(

        @NotBlank(message = "Family name is required")
        @Size(min = 2, max = 100, message = "Family name must be 2-100 characters")
        String name
) {}
