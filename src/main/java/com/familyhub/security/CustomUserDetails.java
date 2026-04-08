package com.familyhub.security;

import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wraps the User entity for Spring Security.
 * Stores only primitive fields — NOT the JPA entity itself —
 * to avoid LazyInitializationException when session is serialized.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final Role role;
    private final Long familyId; // null if user has not joined a family yet
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole();
        this.familyId = user.getFamily() != null ? user.getFamily().getId() : null;
        this.enabled = user.isEnabled();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /** Spring Security uses this as the "username" — we use email. */
    @Override
    public String getUsername() {
        return email;
    }
}
