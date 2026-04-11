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

// @Configuration — klasė su @Bean metodais. Spring ją perskaito startupdaant.
@Configuration
// @EnableWebSecurity — aktyvuoja Spring Security web apsaugą projektui
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    // @Value — įkelia reikšmę iš application.yaml.
    // Sintaksė: ${raktas:numatytoji_reikšmė}
    // Slaptažodžiai/raktai neturi būti hardcoded kode — tik konfigūracijoje
    @Value("${app.remember-me.key}")
    private String rememberMeKey;

    @Value("${app.remember-me.validity-seconds:2592000}")
    private int rememberMeValiditySeconds;

    // @Bean — Spring valdo šio objekto gyvavimo ciklą (Singleton pagal nutylėjimą).
    // SecurityFilterChain — grandinė filtrų, per kuriuos eina kiekvienas HTTP request'as.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Nurodo kurį AuthenticationProvider naudoti prisijungimui
            .authenticationProvider(authenticationProvider())

            // Leidimų taisyklės — tikrinamos iš viršaus į apačią, pirma tinkanti taikoma
            .authorizeHttpRequests(auth -> auth
                // permitAll() — leidžiama visiems, net neprisijungusiems
                .requestMatchers("/login", "/register", "/forgot-password", "/reset-password", "/error").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // hasRole("ADMIN") — tik vartotojai su ROLE_ADMIN rolę
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // hasRole("PARENT") — tik ROLE_PARENT
                .requestMatchers("/family/create", "/family/invite/**", "/tasks/assign/**").hasRole("PARENT")
                // Viskas kita — reikia būti prisijungusiam (bet kokia rolė tinka)
                .anyRequest().authenticated()
            )

            // Prisijungimo formos konfigūracija
            .formLogin(form -> form
                .loginPage("/login")           // Mūsų pačių sukurtas login puslapis
                .usernameParameter("email")    // HTML input name="email" (ne "username")
                .passwordParameter("password") // HTML input name="password"
                .defaultSuccessUrl("/dashboard", true) // Po sėkmingo login → /dashboard
                // true = visada eiti į /dashboard, net jei anksčiau bandei kitą URL
                .failureUrl("/login?error=true") // Nesėkmingo login atveju → prideda ?error
                .permitAll()
            )

            // Atsijungimo konfigūracija
            .logout(logout -> logout
                .logoutUrl("/logout")                        // POST į šį URL atsijungia
                .logoutSuccessUrl("/login?logout=true")      // Po atsijungimo → login puslapis
                .invalidateHttpSession(true)                 // Sunaikina sesiją serveryje
                .deleteCookies("JSESSIONID", "remember-me") // Išvalo cookies naršyklėje
                .permitAll()
            )

            // Remember-me — "prisimink mane" funkcija
            // Veikimas: po sėkmingo login su pažymėtu checkbox sukuriamas cookie.
            // Kito apsilankymo metu Spring Security randa cookie, patikrina jo parašą
            // su rememberMeKey ir automatiškai prisijungia be slaptažodžio.
            .rememberMe(rememberMe -> rememberMe
                .userDetailsService(userDetailsService) // Kaip atnaujinti vartotojo duomenis
                .key(rememberMeKey)                     // Slaptas raktas cookie parašui
                .tokenValiditySeconds(rememberMeValiditySeconds) // 30 dienų galiojimas
                .rememberMeParameter("remember-me")    // HTML checkbox name="remember-me"
                .rememberMeCookieName("remember-me")   // Cookie pavadinimas naršyklėje
            )

            // Sesijų valdymas
            .sessionManagement(session -> session
                // changeSessionId() — apsauga nuo Session Fixation atakos:
                // po prisijungimo sugeneruojamas naujas sesijos ID.
                // Taip niekas negali panaudoti iš anksto žinomo sesijos ID.
                .sessionFixation().changeSessionId()
            );

        return http.build();
    }

    // BCryptPasswordEncoder — vienpusė funkcija slaptažodžiams.
    // "Vienpusė" reiškia: iš hash'o neįmanoma gauti originalaus slaptažodžio.
    // Kiekvieną kartą generuoja skirtingą hash'ą (salt), todėl
    // tas pats slaptažodis duoda skirtingus hash'us — saugiau nei MD5/SHA.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // DaoAuthenticationProvider — Spring Security komponentas kuris:
    // 1. Gauna vartotoją iš DB per userDetailsService
    // 2. Palygina įvestą slaptažodį su hash'u naudodamas passwordEncoder
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // Spring Security 6+ reikalauja UserDetailsService konstruktoriuje
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
