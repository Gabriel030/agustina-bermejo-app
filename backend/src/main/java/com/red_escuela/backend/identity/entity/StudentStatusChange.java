package com.red_escuela.backend.identity.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "student_status_change")
public class StudentStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private StudentStatusChangeType changeType;

    @Column(name = "reason")
    private String reason;

    @Column(name = "actor_person_id", nullable = false)
    private Long actorPersonId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public StudentStatusChange(Long studentId, StudentStatusChangeType changeType, String reason, Long actorPersonId,
                               LocalDateTime occurredAt) {
        this.studentId = studentId;
        this.changeType = changeType;
        this.reason = reason;
        this.actorPersonId = actorPersonId;
        this.occurredAt = occurredAt;
    }
}
