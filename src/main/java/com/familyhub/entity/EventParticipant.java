package com.familyhub.entity;

import com.familyhub.entity.enums.ParticipantType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "event_participants",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "user_id"}),
        @UniqueConstraint(columnNames = {"event_id", "pet_id"}),
        @UniqueConstraint(columnNames = {"event_id", "family_member_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"event", "user", "pet", "familyMember"})
public class EventParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Indicates which of the three fields (user/pet/familyMember) is populated
    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 15)
    private ParticipantType participantType;

    // Only one of these three fields should be non-null per record
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

    // Family member without an account (e.g. young child)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id")
    private FamilyMember familyMember;
}
