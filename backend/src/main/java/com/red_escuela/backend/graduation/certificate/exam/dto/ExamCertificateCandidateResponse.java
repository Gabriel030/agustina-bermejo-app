package com.red_escuela.backend.graduation.certificate.exam.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExamCertificateCandidateResponse(Long registrationId, Long studentId, String studentName, String maskedDocument, ExamCertificateCandidateAvailability availability, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long certificateId) {
}
