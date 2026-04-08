package com.familyhub.entity;

import com.familyhub.entity.enums.ParticipantType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "event_participants",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "user_id"}),
        @UniqueConstraint(columnNames = {"event_id", "pet_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"event", "user", "pet"})
public class EventParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 10)
    private ParticipantType participantType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_id")
    private Pet pet;

}
