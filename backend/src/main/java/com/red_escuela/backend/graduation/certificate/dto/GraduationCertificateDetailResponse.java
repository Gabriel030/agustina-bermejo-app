package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GraduationCertificateDetailResponse(
        Long id,
        String code,
        @Nullable @Schema(pattern = "^\\d{7}/\\d{4}$") String registryNumber,
        GraduationCertificateKind kind,
        GraduationCertificateStatus status,
        String holderFullName,
        @Nullable String holderDocumentType,
        String holderDocumentNumber,
        String titleName,
        @Nullable Integer hours,
        LocalDate graduationDate,
        LocalDateTime issuedAt,
        String verificationUrl,
        @Nullable Integer templateVersionNumber,
        @Nullable LocalDateTime revokedAt,
        @Nullable String revocationReason,
        @Nullable Long replacesCertificateId,
        @Nullable Long replacedByCertificateId) {
}
