package com.familyhub.security;

import com.familyhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// @Service — Spring sukuria šį bean'ą ir įdeda į kontekstą.
// Implementuojame UserDetailsService — Spring Security reikalauja šios sąsajos.
// Ji turi vieną metodą: loadUserByUsername().
@Service
// @RequiredArgsConstructor — Lombok generuoja konstruktorių su final laukais.
// Tai yra "constructor injection" — rekomenduojamas būdas injekcijoms Spring'e.
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    // Spring Security kviečia šį metodą dviem atvejais:
    // 1. Kai vartotojas prisijungia (įveda email + slaptažodį)
    // 2. Kai tikrinamas remember-me cookie kiekvieno request'o metu
    // Parametras "username" čia iš tikrųjų yra email — taip sukonfigūravome SecurityConfig
    @Override
    @Transactional(readOnly = true)
    // readOnly = true — optimizacija: Hibernate žino kad nekeisim duomenų,
    // tad nereikia "dirty checking" (tikrinti ar kas pasikeitė)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                // Method reference: CustomUserDetails::new = user -> new CustomUserDetails(user)
                // Kiekvienam rastam User sukuria CustomUserDetails objektą
                .map(CustomUserDetails::new)
                // Jei vartotojo su tokiu email nėra — Spring Security rodys "Invalid credentials"
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
