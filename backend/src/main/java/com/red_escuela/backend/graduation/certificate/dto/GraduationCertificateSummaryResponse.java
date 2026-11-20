package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GraduationCertificateSummaryResponse(
        Long id,
        String code,
        @Nullable @Schema(pattern = "^\\d{7}/\\d{4}$") String registryNumber,
        GraduationCertificateKind kind,
        GraduationCertificateStatus status,
        String holderFullName,
        String holderDocumentNumber,
        String titleName,
        LocalDate graduationDate,
        LocalDateTime issuedAt,
        @Nullable Integer templateVersionNumber) {
}
