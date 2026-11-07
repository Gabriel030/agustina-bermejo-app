package com.red_escuela.backend.graduation.certificate.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PublicCertificateView(
        PublicCertificateDocumentKind documentKind,
        PublicCertificateStatus status,
        String holderFullName,
        String maskedDocument,
        String titleKind,
        String titleName,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate graduationDate,
        LocalDateTime issuedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime expiresAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime revokedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String registryNumber,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer hours) {
}
