package com.red_escuela.backend.graduation.certificate.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record GraduationCertificateReviewBatchItemResponse(
        Long graduationRequestId,
        CertificateReviewBatchOutcome outcome,
        @Schema(nullable = true) @Nullable GraduationCertificateReviewResponse review,
        @Nullable String errorCode) {
}
