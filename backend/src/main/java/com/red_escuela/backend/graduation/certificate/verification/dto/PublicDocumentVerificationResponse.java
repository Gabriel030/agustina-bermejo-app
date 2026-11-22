package com.red_escuela.backend.graduation.certificate.verification.dto;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record PublicDocumentVerificationResponse(CertificateDocumentType documentType, DocumentValidityStatus status, String holderFullName, String maskedDocument, LocalDateTime issuedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime expiresAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime revokedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime replacedAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String registryNumber) {
}
