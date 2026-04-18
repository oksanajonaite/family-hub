package com.familyhub.config;

import com.familyhub.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
// @EnableWebSecurity — activates Spring Security's web security support for the project
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Value("${app.remember-me.key}")
    private String rememberMeKey;

    @Value("${app.remember-me.validity-seconds:2592000}")
    private int rememberMeValiditySeconds;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        http
            .authenticationProvider(provider)

            // Authorization rules — evaluated top to bottom, first match wins
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/forgot-password", "/reset-password", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Family management actions restricted to PARENT role
                .requestMatchers("/family/create", "/family/invite/**", "/family/members/*/remove").hasRole("PARENT")
                // Everything else requires any authenticated user
                .anyRequest().authenticated()
            )

            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")    // Our form uses "email", not "username"
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true) // true = always go to /dashboard, even if user tried another URL first
                .failureUrl("/login?error=true")
                .permitAll()
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)                 // Destroy the server-side session
                .deleteCookies("JSESSIONID", "remember-me") // Clear cookies in the browser
                .permitAll()
            )

            // Remember-me: on successful login with the checkbox checked, a signed cookie is created.
            // On the next visit, Spring Security reads the cookie, verifies its signature using rememberMeKey,
            // and logs the user in automatically without a password.
            .rememberMe(rememberMe -> rememberMe
                .userDetailsService(userDetailsService)
                .key(rememberMeKey)                          // Secret key used to sign the cookie
                .tokenValiditySeconds(rememberMeValiditySeconds) // 30-day validity
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("remember-me")
            )

            // Session fixation protection: generate a new session ID after login
            // so an attacker cannot reuse a session ID they obtained before authentication
            .sessionManagement(session -> session
                .sessionFixation().changeSessionId()
            );

        return http.build();
    }

    // BCrypt is a one-way hash function — the original password cannot be recovered from the hash.
    // Each call generates a different hash (due to a random salt),
    // so the same password always produces a different hash — more secure than MD5/SHA.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
