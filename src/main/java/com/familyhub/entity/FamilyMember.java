package com.familyhub.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

// FamilyMember — a person without an account (e.g. young child, grandparent).
// Unlike User: cannot log in, has no email or password.
// Used as an event participant and task assignee — managed by a PARENT on their behalf.
@Entity
@Table(name = "family_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "family")
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // Each member belongs to exactly one family
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @Column(nullable = false, length = 80)
    private String name;

    // Optional date of birth — used for birthday reminders (v2)
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    // Stores the S3 key (e.g. "members/uuid.jpg") of the member's photo — NOT a public URL.
    // Null when no photo uploaded — UI falls back to child icon.
    // To display: PhotoController generates a fresh pre-signed URL via /api/photo/member/{id}.
    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
