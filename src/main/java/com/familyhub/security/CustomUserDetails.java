package com.familyhub.security;

import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Spring Security requires a UserDetails object to be stored in the session.
// We cannot put the User JPA entity directly into the session because:
// 1. The session is serialized — serializing a JPA entity with lazy relationships is unsafe
// 2. Hibernate would try to load lazy data from a closed session on each request
// Solution: a simple class with only primitive/serializable fields.
@Getter
public class CustomUserDetails implements UserDetails {

    // Only primitive/simple fields — no JPA entities, no lazy relationships
    private final Long id;
    private final String email;
    private final String displayName;
    private final String password;
    private final Role role;
    private final Long familyId; // null if the user has not joined a family yet
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;
    private final String avatarKey; // null when no photo uploaded — UI falls back to initials

    // Converts a User entity into this lightweight object.
    // All required data is extracted immediately while the Hibernate session is still open.
    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.password = user.getPassword();
        this.role = user.getRole();
        // Take only the family ID, not the whole Family entity, to avoid lazy loading issues
        this.familyId = user.getFamily() != null ? user.getFamily().getId() : null;
        this.enabled = user.isEnabled();
        // Spring Security expects the "ROLE_" prefix — PARENT becomes "ROLE_PARENT".
        // This makes hasRole("PARENT") in SecurityConfig work correctly.
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        this.avatarKey = user.getAvatarUrl();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    // Spring Security calls this method internally (remember-me, session).
    // We use email as the "username" — configured in SecurityConfig with usernameParameter("email").
    @Override
    public String getUsername() {
        return email;
    }
}
