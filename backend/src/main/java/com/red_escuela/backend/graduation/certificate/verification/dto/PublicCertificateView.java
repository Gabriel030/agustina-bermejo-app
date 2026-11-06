package com.red_escuela.backend.graduation.certificate.verification.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PublicCertificateView(
        PublicCertificateDocumentKind documentKind,
        PublicCertificateStatus status,
        String holderFullName,
        String maskedDocument,
        String titleKind,
        String titleName,
        LocalDate graduationDate,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        String registryNumber,
        Integer hours) {
}
