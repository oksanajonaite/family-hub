package com.familyhub.util;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

// Reloads the currently authenticated user from the DB and replaces the
// security context with fresh data.
//
// Needed after operations that change role, family membership, or display name
// so the updated values are visible immediately — without forcing a re-login.
//
// Extracted from FamilyController and ProfileController to avoid duplication (DRY).
@Component
@RequiredArgsConstructor
public class SecurityContextHelper {

    private final UserDetailsService userDetailsService;

    public void refresh(String email) {
        UserDetails fresh = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
