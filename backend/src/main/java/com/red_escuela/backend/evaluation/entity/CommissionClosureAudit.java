package com.red_escuela.backend.evaluation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commission_closure_audit")
public class CommissionClosureAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closure_id", nullable = false)
    private Long closureId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private ClosureAuditAction action;

    @Column(name = "report_version", nullable = false)
    private int reportVersion;

    @Column(name = "actor_person_id", nullable = false)
    private Long actorPersonId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
