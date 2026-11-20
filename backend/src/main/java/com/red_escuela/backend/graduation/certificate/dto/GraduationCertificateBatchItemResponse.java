package com.red_escuela.backend.graduation.certificate.dto;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record GraduationCertificateBatchItemResponse(
        Long enrollmentId,
        GraduationCertificateKind kind,
        BatchItemOutcome outcome,
        @Schema(nullable = true) GraduationCertificateResponse certificate,
        @Nullable String errorCode) {
}
