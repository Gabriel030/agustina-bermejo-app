package com.red_escuela.backend.graduation.certificate.regular.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.StandingBasis;

import java.time.LocalDateTime;

public record RegularCertificateResponse(Long id, String code, Long studentId, String studentName,
                                         @Schema(description = "Nombre de la carrera (PROGRAM) o del curso (COURSE) al emitir") String programName,
                                         RegularityKind kind, Long enrollmentId,
                                         RegularCertificateStatus status, LocalDateTime issuedAt, LocalDateTime expiresAt,
                                         StandingBasis standingBasis, String billingModeAtIssue, String verificationUrl) {
}
