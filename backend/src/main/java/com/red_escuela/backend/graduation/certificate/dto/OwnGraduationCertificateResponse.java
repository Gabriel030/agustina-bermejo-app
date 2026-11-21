package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OwnGraduationCertificateResponse(
        Long id,
        @Nullable @Schema(pattern = "^\\d{7}/\\d{4}$") String registryNumber,
        GraduationCertificateKind kind,
        GraduationCertificateStatus status,
        String titleName,
        LocalDate graduationDate,
        LocalDateTime issuedAt,
        @Nullable LocalDateTime revokedAt,
        boolean downloadable,
        boolean previewable) {
}
