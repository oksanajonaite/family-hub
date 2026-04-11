package com.familyhub.security;

import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Kodėl ši klasė egzistuoja:
// Spring Security reikalauja UserDetails objekto sesijoje.
// NEGALIME tiesiog įdėti User (JPA entity) į sesiją, nes:
// 1. Sesija serializuojama — JPA entity su lazy ryšiais serializuoti pavojinga
// 2. Kiekvieną request'ą Hibernate bandytų gauti lazy duomenis iš uždarytos sesijos
// Sprendimas: sukurti paprastą klasę tik su primityviais laukais.
@Getter
public class CustomUserDetails implements UserDetails {

    // Saugome tik paprastus laukus — jokių JPA entity, jokių lazy ryšių
    private final Long id;
    private final String email;
    private final String password;
    private final Role role;
    private final Long familyId; // null — jei vartotojas dar be šeimos
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    // Konstruktorius: konvertuoja User entity į šį paprastą objektą.
    // Iškart paima visus reikalingus duomenis kol Hibernate sesija dar aktyvi.
    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole();
        // Ternary operatorius: jei family nėra (null) — familyId = null,
        // jei yra — paima tik id (ne visą objektą)
        this.familyId = user.getFamily() != null ? user.getFamily().getId() : null;
        this.enabled = user.isEnabled();
        // GrantedAuthority — Spring Security rolių sistema.
        // SVARBU: Spring tikisi prefikso "ROLE_", todėl PARENT tampa "ROLE_PARENT".
        // Tada SecurityConfig hasRole("PARENT") veikia teisingai.
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

    // Spring Security "username" konceptas — mes naudojame email vietoj username.
    // Šis metodas kviečiamas Spring Security vidinių mechanizmų (remember-me, sesija).
    @Override
    public String getUsername() {
        return email;
    }
}
