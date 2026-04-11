package com.familyhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kiekvienam vartotojui vienu metu gali būti tik vienas aktyvus token'as.
    // Kai generuojamas naujas — senas ištrinamas.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // UUID token'as — atsitiktinis, neišspėjamas.
    // unique = true — du vartotojai negali turėti to paties token'o (nors praktiškai neįmanoma)
    @Column(nullable = false, unique = true)
    private String token;

    // Token'as galioja 1 valandą — po to nebegali būti naudojamas
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
