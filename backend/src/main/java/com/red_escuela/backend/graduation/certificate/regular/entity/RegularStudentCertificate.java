package com.red_escuela.backend.graduation.certificate.regular.entity;

import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
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
@Table(name = "regular_student_certificate")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegularStudentCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "code", nullable = false, length = 26, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private RegularityKind kind;

    @Column(name = "program_enrollment_id", updatable = false)
    private Long programEnrollmentId;

    @Column(name = "course_enrollment_id", updatable = false)
    private Long courseEnrollmentId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegularCertificateStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "validity_days", nullable = false, updatable = false)
    private int validityDays;

    @Column(name = "issued_by_id", nullable = false, updatable = false)
    private Long issuedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_channel", nullable = false, length = 20, updatable = false)
    private RegularCertificateChannel issueChannel;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "billing_mode_at_issue", nullable = false, length = 10, updatable = false)
    private String billingModeAtIssue;

    @Enumerated(EnumType.STRING)
    @Column(name = "standing_basis", nullable = false, length = 30, updatable = false)
    private StandingBasis standingBasis;

    @Column(name = "standing_confirmed_by_id", updatable = false)
    private Long standingConfirmedById;

    @Column(name = "standing_confirmed_at", updatable = false)
    private LocalDateTime standingConfirmedAt;

    @Column(name = "standing_evidence", length = 500, updatable = false)
    private String standingEvidence;

    @Column(name = "exception_id", updatable = false)
    private Long exceptionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private CertificateSnapshot snapshot;

    @Column(name = "template_ref", nullable = false, length = 40, updatable = false)
    private String templateRef;

    @Column(name = "registry_number", length = 20)
    private String registryNumber;

    @Column(name = "pdf_sha256", nullable = false, length = 64)
    private String pdfSha256;

    @Column(name = "pdf_path", nullable = false, length = 300)
    private String pdfPath;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    public boolean isExpiredAt(LocalDateTime now) {
        return status == RegularCertificateStatus.VALID && expiresAt.isBefore(now);
    }

    public void expire() {
        if (status == RegularCertificateStatus.VALID) {
            this.status = RegularCertificateStatus.EXPIRED;
        }
    }

    public void replacePdf(String newSha256, String newPath) {
        this.pdfSha256 = newSha256;
        this.pdfPath = newPath;
    }

    public Long enrollmentId() {
        return kind == RegularityKind.PROGRAM ? programEnrollmentId : courseEnrollmentId;
    }

    public CertificateCode certificateCode() {
        return new CertificateCode(code);
    }
}
