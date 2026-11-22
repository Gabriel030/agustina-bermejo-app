package com.red_escuela.backend.graduation.certificate.approvedsubjects.dto;

import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ApprovedSubjectsCertificateResponse(Long id, String code, Long studentId, String studentName, String programName, Long enrollmentId, DocumentValidityStatus status, LocalDateTime issuedAt, LocalDateTime expiresAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Grade averageGrade, int approvedSubjectsCount, int totalSubjectsCount, int approvedPercentage, String verificationUrl) {
}
