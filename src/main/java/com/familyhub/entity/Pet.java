package com.familyhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "family")
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @Column(nullable = false, length = 80)
    private String name;

    // Free-text type — user can enter any animal type (e.g. "Chinchilla", "Parrot")
    @Column(length = 50)
    private String type;

    // Optional date of birth — used for birthday reminders (v2)
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    // Stores the S3 key (e.g. "pets/uuid.jpg") of the pet's photo — NOT a public URL.
    // Null when no photo uploaded — UI falls back to pet type icon.
    // To display: PhotoController generates a fresh pre-signed URL via /api/photo/pet/{id}.
    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
