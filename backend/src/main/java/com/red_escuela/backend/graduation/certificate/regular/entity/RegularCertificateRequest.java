package com.red_escuela.backend.graduation.certificate.regular.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "regular_certificate_request")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegularCertificateRequest {

    public static final String CANCELLED_BY_STUDENT = "CANCELLED_BY_STUDENT";
    public static final String REJECTED_BY_STAFF = "REJECTED_BY_STAFF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private RegularityKind kind;

    @Column(name = "program_enrollment_id", updatable = false)
    private Long programEnrollmentId;

    @Column(name = "course_enrollment_id", updatable = false)
    private Long courseEnrollmentId;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20, updatable = false)
    private RegularCertificateChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private RegularRequestOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasons", nullable = false, columnDefinition = "jsonb")
    private List<String> reasons;

    @Column(name = "certificate_id")
    private Long certificateId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "resolved_by_id")
    private Long resolvedById;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public static RegularCertificateRequest record(RegularityKind kind, Long enrollmentId, Long studentId, Long requestedById,
                                                   LocalDateTime requestedAt, RegularCertificateChannel channel,
                                                   RegularRequestOutcome outcome, List<String> reasons) {
        return RegularCertificateRequest.builder()
                .studentId(studentId)
                .kind(kind)
                .programEnrollmentId(kind == RegularityKind.PROGRAM ? enrollmentId : null)
                .courseEnrollmentId(kind == RegularityKind.COURSE ? enrollmentId : null)
                .requestedById(requestedById)
                .requestedAt(requestedAt)
                .channel(channel)
                .outcome(outcome)
                .reasons(List.copyOf(reasons))
                .build();
    }

    public void linkCertificate(Long newCertificateId) {
        this.certificateId = newCertificateId;
    }

    public boolean isPending() {
        return outcome == RegularRequestOutcome.PENDING_STAFF;
    }

    public void resolveAsIssued(Long newCertificateId) {
        this.outcome = RegularRequestOutcome.ISSUED;
        this.certificateId = newCertificateId;
    }

    public void cancelByStudent() {
        this.outcome = RegularRequestOutcome.REJECTED;
        this.reasons = List.of(CANCELLED_BY_STUDENT);
    }

    public void rejectByStaff(String reason, Long staffId, LocalDateTime now) {
        this.outcome = RegularRequestOutcome.REJECTED;
        this.reasons = List.of(REJECTED_BY_STAFF);
        this.rejectionReason = reason;
        this.resolvedById = staffId;
        this.resolvedAt = now;
    }

    public boolean wasRejectedByStaff() {
        return outcome == RegularRequestOutcome.REJECTED && rejectionReason != null;
    }

    public boolean wasCancelledByStudent() {
        return outcome == RegularRequestOutcome.REJECTED && reasons.contains(CANCELLED_BY_STUDENT);
    }

    public Long enrollmentId() {
        return kind == RegularityKind.PROGRAM ? programEnrollmentId : courseEnrollmentId;
    }
}
