package com.red_escuela.backend.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

@Getter
@Setter
@Entity
@Table(name = "graduation_request")
public class GraduationRequest {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_enrollment_id", nullable = false)
    private ProgramEnrollment programEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GraduationRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    private Person verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private Person decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
