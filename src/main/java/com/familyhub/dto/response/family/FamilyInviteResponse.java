package com.familyhub.dto.response.family;

import java.time.LocalDateTime;

public record FamilyInviteResponse(

        String code,
        LocalDateTime expiresAt
) {}

