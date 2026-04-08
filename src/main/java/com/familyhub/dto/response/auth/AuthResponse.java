package com.familyhub.dto.response.auth;

import com.familyhub.entity.enums.Role;

public record AuthResponse(

        Long userId,
        String email,
        String displayName,
        Role role
) {}
