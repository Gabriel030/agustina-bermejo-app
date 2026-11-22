package com.red_escuela.backend.graduation.certificate.exam.dto;

import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ExamCertificateResponse(Long id, String code, Long registrationId, Long examBoardId, Long studentId, String studentName, String subjectName, LocalDate examDate, String examBoardLabel, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) RecipientType recipientType, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String recipientText, DocumentValidityStatus status, DocumentOrigin origin, LocalDateTime issuedAt, LocalDateTime expiresAt, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String registryNumber, String verificationUrl) {
}
