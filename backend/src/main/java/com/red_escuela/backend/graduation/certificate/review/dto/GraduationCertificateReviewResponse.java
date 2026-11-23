package com.red_escuela.backend.graduation.certificate.review.dto;

import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.springframework.lang.Nullable;

public record GraduationCertificateReviewResponse(
        Long graduationRequestId,
        Long enrollmentId,
        Long studentId,
        String studentFullName,
        String documentNumber,
        String titleName,
        @Schema(nullable = true) @Nullable String registryNumber,
        @Schema(nullable = true) @Nullable String legacyTitleNumber,
        CertificateReviewStatus reviewStatus,
        @Schema(nullable = true) @Nullable Long reviewedById,
        @Schema(nullable = true) @Nullable String reviewedByName,
        @Schema(nullable = true) @Nullable LocalDateTime reviewedAt) {
}
