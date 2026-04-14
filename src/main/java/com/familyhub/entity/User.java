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

// Why NOT @Data:
// @Data generates equals() and hashCode() based on ALL fields,
// including lazy-loaded relations (e.g. family). This causes
// LazyInitializationException or infinite recursion.
// Instead we use @EqualsAndHashCode with only the id field:
@EqualsAndHashCode(onlyExplicitlyIncluded = true)

// Prevents toString() from accessing lazy fields (family),
// which would trigger an extra SQL query or throw an error
@ToString(exclude = "family")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // This field is used in equals() and hashCode().
    // Meaning: two User objects are "equal" only if their ids match.
    // Correct JPA behavior — entity identity is determined by the DB key, not field values.
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    // The field is named "password", but the DB column is "password_hash".
    // We store the hashed password (BCrypt), not plaintext.
    @Column(name = "password_hash", nullable = false, length = 255)
    private String password;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // LAZY — Family is loaded from DB only when getFamily() is actually called.
    // This avoids unnecessary SQL queries to the families table every time
    // we just use the User object.
    @ManyToOne(fetch = FetchType.LAZY)
    // At DB level — column "family_id" in this table (FK to families.id)
    @JoinColumn(name = "family_id")
    private Family family; // null — if the user has not joined a family yet

    // Optional date of birth — for PARENT/KID users only.
    // null — if the user did not provide it during registration.
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private boolean enabled = true; // false — account is blocked

    // @CreationTimestamp — Hibernate automatically fills this field at persist time.
    // Better than = LocalDateTime.now() in field initializers,
    // because it fires exactly on persist(), not when the object is constructed.
    // updatable = false — the value never changes after the first save.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
