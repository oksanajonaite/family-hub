package com.familyhub.dto.request.family;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinFamilyRequest(

        @NotBlank(message = "Invite code is required")
        @Size(min = 4, max = 32, message = "Invite code must be 4-32 characters")
        String inviteCode
) {}

