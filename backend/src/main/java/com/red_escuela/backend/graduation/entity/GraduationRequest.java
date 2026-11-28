package com.red_escuela.backend.graduation.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;

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

    @Column(name = "registry_number", length = 20)
    private String registryNumber;

    @Column(name = "legacy_title_number", length = 50)
    private String legacyTitleNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_review_status", nullable = false)
    private CertificateReviewStatus certificateReviewStatus = CertificateReviewStatus.NOT_REQUIRED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_reviewed_by_id")
    private Person certificateReviewedBy;

    @Column(name = "certificate_reviewed_at")
    private LocalDateTime certificateReviewedAt;

    @Column(name = "outdated_at")
    private LocalDateTime outdatedAt;

    public boolean markOutdated(LocalDateTime flaggedAt) {
        if (outdatedAt != null) {
            return false;
        }
        outdatedAt = flaggedAt;
        return true;
    }

    public void clearOutdated() {
        outdatedAt = null;
    }

    public void requireAwaitingDirectorDecision() {
        if (status != GraduationRequestStatus.ADMIN_VERIFIED) {
            throw new InvalidGraduationRequestStateException(id);
        }
    }

    public void closeBecauseEnrollmentEnded(String reason, LocalDateTime closedAt) {
        status = GraduationRequestStatus.REJECTED;
        rejectionReason = reason;
        decidedAt = closedAt;
    }

    public void approve(Person director, LocalDateTime approvedAt) {
        requireAwaitingDirectorDecision();
        status = GraduationRequestStatus.APPROVED;
        decidedBy = director;
        decidedAt = approvedAt;
        programEnrollment.complete(approvedAt);
    }
}
