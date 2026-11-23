package com.red_escuela.backend.graduation.debtexception.dto;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record DebtExceptionResponse(Long id, Long studentId, String studentName, CertificateDocumentType documentType, DebtExceptionStatus status, String reason, String issuedBy, LocalDateTime issuedAt, LocalDateTime expiresAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime usedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long documentId, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String verificationUrl) {
}
