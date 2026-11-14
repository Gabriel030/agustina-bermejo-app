package com.red_escuela.backend.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor_person_id")
    private Long actorPersonId;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private AuditEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "before_value", length = 100)
    private String beforeValue;

    @Column(name = "after_value", length = 100)
    private String afterValue;

    @Column(name = "reason", length = 500)
    private String reason;
}
