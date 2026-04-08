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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Value("${app.remember-me.key}")
    private String rememberMeKey;

    @Value("${app.remember-me.validity-seconds:2592000}") // default: 30 days
    private int rememberMeValiditySeconds;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                // Public pages
                .requestMatchers("/login", "/register", "/error").permitAll()
                // Static assets
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // ADMIN-only
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // PARENT-only actions
                .requestMatchers("/family/create", "/family/invite/**", "/tasks/assign/**").hasRole("PARENT")
                // Everything else requires login
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            )
            .rememberMe(rememberMe -> rememberMe
                .userDetailsService(userDetailsService)
                .key(rememberMeKey)
                .tokenValiditySeconds(rememberMeValiditySeconds)
                .rememberMeParameter("remember-me")
                .rememberMeCookieName("remember-me")
            )
            .sessionManagement(session -> session
                .sessionFixation().changeSessionId()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
