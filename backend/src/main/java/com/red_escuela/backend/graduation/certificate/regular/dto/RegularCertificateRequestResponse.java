package com.red_escuela.backend.graduation.certificate.regular.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.List;

public record RegularCertificateRequestResponse(Long id, Long studentId, String studentName,
                                                @Schema(description = "Nombre de la carrera (PROGRAM) o del curso (COURSE)") String programName,
                                                RegularityKind kind, Long enrollmentId,
                                                RegularCertificateChannel channel, RegularRequestOutcome outcome,
                                                List<String> reasons, LocalDateTime requestedAt, @Schema(nullable = true) Long certificateId,
                                                @Schema(nullable = true) String rejectionReason,
                                                @Schema(nullable = true, description = "Deuda total del alumno; null con billing apagado") Money totalDue,
                                                @Schema(nullable = true, description = "Cuotas vencidas; null con billing apagado") Integer overdueInstallments,
                                                @Schema(nullable = true, description = "null con billing apagado") Boolean hasDebt,
                                                @Schema(nullable = true) Long exceptionId,
                                                @Schema(nullable = true) CertificateExceptionStatus exceptionStatus,
                                                @Schema(nullable = true) LocalDateTime exceptionValidUntil) {
}
