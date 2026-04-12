package com.familyhub.entity;

import com.familyhub.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

// Kodėl NE @Data:
// @Data generuoja equals() ir hashCode() pagal VISUS laukus,
// įskaitant lazy-loaded ryšius (pvz. family). Tai sukelia
// LazyInitializationException arba begalinę rekursiją.
// Vietoj to naudojame @EqualsAndHashCode su tik id lauku:
@EqualsAndHashCode(onlyExplicitlyIncluded = true)

// Neleidžia toString() kreiptis į lazy laukus (family),
// nes tai sukeltų papildomą SQL užklausą arba klaidą
@ToString(exclude = "family")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Šis laukas bus naudojamas equals() ir hashCode() metoduose.
    // Tai reiškia: du User objektai yra "lygūs" tik jei jų id sutampa.
    // Teisingas elgesys su JPA — entity tapatybę nustato DB raktas, ne laukai.
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // Laukas vadinasi "password", bet DB stulpelis — "password_hash".
    // Saugome jau užšifruotą slaptažodį (BCrypt), ne plaintext.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // LAZY — Family objektas iš DB kraunamas TIK tada, kai iš tikrųjų
    // kviečiame getFamily(). Taip netikri SQL į families lentelę kiekvieną
    // kartą kai tiesiog naudojame User objektą.
    @ManyToOne(fetch = FetchType.LAZY)
    // DB lygmenyje — stulpelis "family_id" šioje lentelėje (FK į families.id)
    @JoinColumn(name = "family_id")
    private Family family; // null — jei vartotojas dar neprisijungęs prie šeimos

    // Neprivaloma gimimo data — tik PARENT/KID vartotojams.
    // null — jei vartotojas nepateikė registracijos metu.
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private boolean enabled = true; // false — paskyra užblokuota

    // @CreationTimestamp — Hibernate automatiškai užpildo šį lauką
    // išsaugojimo momentu. Geriau nei = LocalDateTime.now() laukų inicializacijoje,
    // nes veikia tiksliai persist() metu, o ne objekto sukūrimo metu.
    // updatable = false — reikšmė niekada nesikeičia po pirmo išsaugojimo.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
