package com.red_escuela.backend.graduation.certificate.approvedsubjects.entity;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
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

@Entity
@Table(name = "approved_subjects_certificate")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovedSubjectsCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "code", nullable = false, length = 26, updatable = false)
    private String code;

    @Column(name = "program_enrollment_id", nullable = false, updatable = false)
    private Long programEnrollmentId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApprovedSubjectsCertificateStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "validity_days", nullable = false, updatable = false)
    private int validityDays;

    @Column(name = "issued_by_id", nullable = false, updatable = false)
    private Long issuedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", length = 30, updatable = false)
    private RecipientType recipientType;

    @Column(name = "recipient_text", length = 80, updatable = false)
    private String recipientText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private ApprovedSubjectsSnapshot snapshot;

    @Column(name = "template_ref", nullable = false, length = 40, updatable = false)
    private String templateRef;

    @Column(name = "render_engine_version", nullable = false, length = 20, updatable = false)
    private String renderEngineVersion;

    @Column(name = "registry_number", length = 20)
    private String registryNumber;

    @Column(name = "pdf_sha256", nullable = false, length = 64, updatable = false)
    private String pdfSha256;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    public static ApprovedSubjectsCertificate createValid(Long programEnrollmentId, Long studentId, CertificateCode code,
                                                       ApprovedSubjectsSnapshot snapshot, String pdfSha256,
                                                       String renderEngineVersion, String registryNumber,
                                                       LocalDateTime issuedAt, int validityDays, Long issuedById) {
        return ApprovedSubjectsCertificate.builder()
                .code(code.value())
                .programEnrollmentId(programEnrollmentId)
                .studentId(studentId)
                .status(ApprovedSubjectsCertificateStatus.VALID)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusDays(validityDays))
                .validityDays(validityDays)
                .issuedById(issuedById)
                .recipientType(snapshot.recipientType())
                .recipientText(snapshot.recipientText())
                .snapshot(snapshot)
                .templateRef(snapshot.templateRef())
                .renderEngineVersion(renderEngineVersion)
                .registryNumber(registryNumber)
                .pdfSha256(pdfSha256)
                .build();
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return status == ApprovedSubjectsCertificateStatus.VALID && !expiresAt.isAfter(now);
    }

    public void expire() {
        if (status == ApprovedSubjectsCertificateStatus.VALID) {
            this.status = ApprovedSubjectsCertificateStatus.EXPIRED;
        }
    }

    public boolean isRevocable() {
        return status == ApprovedSubjectsCertificateStatus.VALID;
    }

    public void revoke(String reason, Long revokedById, LocalDateTime revokedAt) {
        this.status = ApprovedSubjectsCertificateStatus.REVOKED;
        this.revocationReason = reason;
        this.revokedById = revokedById;
        this.revokedAt = revokedAt;
    }

    public CertificateCode certificateCode() {
        return new CertificateCode(code);
    }
}
