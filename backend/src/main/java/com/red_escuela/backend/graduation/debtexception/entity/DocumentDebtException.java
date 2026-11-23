package com.red_escuela.backend.graduation.debtexception.entity;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.debtexception.dto.DebtExceptionStatus;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "document_debt_exception")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentDebtException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "student_id", nullable = false, updatable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40, updatable = false)
    private CertificateDocumentType documentType;

    @Column(name = "enrollment_id", updatable = false)
    private Long enrollmentId;

    @Column(name = "registration_id", updatable = false)
    private Long registrationId;

    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DebtExceptionStatus status;

    @Column(name = "issued_by_id", nullable = false, updatable = false)
    private Long issuedById;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "used_by_id")
    private Long usedById;

    public static DocumentDebtException issue(Long studentId, CertificateDocumentType documentType, Long enrollmentId,
                                              Long registrationId, String reason, Long issuedById,
                                              LocalDateTime issuedAt, int validityDays) {
        DocumentDebtException exception = new DocumentDebtException();
        exception.studentId = studentId;
        exception.documentType = documentType;
        exception.enrollmentId = enrollmentId;
        exception.registrationId = registrationId;
        exception.reason = reason.strip();
        exception.status = DebtExceptionStatus.ACTIVE;
        exception.issuedById = issuedById;
        exception.issuedAt = issuedAt;
        exception.expiresAt = issuedAt.plusDays(validityDays);
        return exception;
    }
}
