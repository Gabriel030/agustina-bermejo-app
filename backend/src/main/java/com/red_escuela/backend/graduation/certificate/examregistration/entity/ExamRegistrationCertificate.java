package com.red_escuela.backend.graduation.certificate.examregistration.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_registration_certificate")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamRegistrationCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "code", nullable = false, length = 26, updatable = false)
    private String code;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Column(name = "registration_id", nullable = false, updatable = false)
    private Long registrationId;

    @Column(name = "exam_board_id", nullable = false, updatable = false)
    private Long examBoardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentValidityStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "issued_by_id", nullable = false, updatable = false)
    private Long issuedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", length = 30, updatable = false)
    private RecipientType recipientType;

    @Column(name = "recipient_text", length = 80, updatable = false)
    private String recipientText;

    @Column(name = "registry_number", length = 20)
    private String registryNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
    private CertificateSnapshot snapshot;

    @Column(name = "template_ref", nullable = false, length = 40, updatable = false)
    private String templateRef;

    @Column(name = "pdf_sha256", nullable = false, length = 64, updatable = false)
    private String pdfSha256;

    @Column(name = "pdf_path", nullable = false, length = 300)
    private String pdfPath;

    @Column(name = "render_engine_version", length = 20)
    private String renderEngineVersion;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by_id")
    private Long revokedById;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    public CertificateCode certificateCode() {
        return new CertificateCode(code);
    }

    public void replacePdfPath(String newPath) {
        this.pdfPath = newPath;
    }

    public boolean isValidAt(LocalDateTime moment) {
        return status == DocumentValidityStatus.VALID && moment.isBefore(expiresAt);
    }
}
