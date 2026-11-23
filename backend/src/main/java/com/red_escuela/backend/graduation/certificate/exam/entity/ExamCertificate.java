package com.red_escuela.backend.graduation.certificate.exam.entity;

import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "exam_certificate")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "code", nullable = false, length = 26, updatable = false)
    private String code;

    @Column(name = "registration_id", nullable = false, updatable = false)
    private Long registrationId;

    @Column(name = "exam_board_id", nullable = false, updatable = false)
    private Long examBoardId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentValidityStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20, updatable = false)
    private DocumentOrigin origin;

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
    private CertificateSnapshot snapshot;

    @Column(name = "template_ref", nullable = false, length = 40, updatable = false)
    private String templateRef;

    @Column(name = "registry_number", length = 20)
    private String registryNumber;

    @Column(name = "pdf_sha256", nullable = false, length = 64)
    private String pdfSha256;

    @Column(name = "render_engine_version", length = 20, updatable = false)
    private String renderEngineVersion;

    @Column(name = "pdf_path", nullable = false, length = 300)
    private String pdfPath;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    public boolean isExpiredAt(LocalDateTime now) {
        return status == DocumentValidityStatus.VALID && expiresAt.isBefore(now);
    }

    public void expire() {
        if (status == DocumentValidityStatus.VALID) {
            this.status = DocumentValidityStatus.EXPIRED;
        }
    }

    public boolean isRevocable() {
        return status == DocumentValidityStatus.VALID;
    }

    public void revoke(String reason, Long directorId, LocalDateTime now) {
        this.status = DocumentValidityStatus.REVOKED;
        this.revokedAt = now;
        this.revokedById = directorId;
        this.revocationReason = reason;
    }

    public boolean isPdfRegenerationVerifiable() {
        return renderEngineVersion != null;
    }

    public void replacePdf(String newSha256, String newPath) {
        this.pdfSha256 = newSha256;
        this.pdfPath = newPath;
    }

    public CertificateCode certificateCode() {
        return new CertificateCode(code);
    }
}
