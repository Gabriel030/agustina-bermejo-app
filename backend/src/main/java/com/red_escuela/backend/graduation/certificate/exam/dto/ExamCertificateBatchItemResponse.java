package com.red_escuela.backend.graduation.certificate.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExamCertificateBatchItemResponse(Long registrationId, String studentName, ExamCertificateBatchOutcome outcome, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long certificateId) {
}
