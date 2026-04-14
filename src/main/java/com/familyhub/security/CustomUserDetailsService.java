package com.familyhub.security;

import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Implements UserDetailsService — required by Spring Security for loading users during authentication.
// Spring Security calls loadUserByUsername() in two cases:
//   1. When a user logs in (email + password form)
//   2. On every request when validating the remember-me cookie
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    // readOnly = true — Hibernate skips dirty checking since we are only reading data
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                // CustomUserDetails::new is equivalent to user -> new CustomUserDetails(user)
                .map(CustomUserDetails::new)
                // If not found, Spring Security will show "Invalid credentials" to the user
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
