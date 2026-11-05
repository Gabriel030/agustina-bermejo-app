package com.red_escuela.backend.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "identity_contact_change_log")
public class ContactChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "actor_person_id", nullable = false)
    private Long actorPersonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ContactChangeType changeType;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public ContactChangeLog(Long personId, Long actorPersonId, ContactChangeType changeType, String oldValue,
                            String newValue, LocalDateTime occurredAt) {
        this.personId = personId;
        this.actorPersonId = actorPersonId;
        this.changeType = changeType;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
    }
}
