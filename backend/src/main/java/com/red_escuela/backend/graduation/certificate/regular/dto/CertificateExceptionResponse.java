package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CertificateExceptionResponse(Long id, Long studentId, RegularityKind kind, Long enrollmentId, OrganismKind organismKind,
                                           String organismReference, String reason, CertificateExceptionStatus status, Long requestedById,
                                           LocalDateTime requestedAt, @Schema(nullable = true) Long decidedById,
                                           @Schema(nullable = true) LocalDateTime decidedAt, @Schema(nullable = true) String decisionReason,
                                           @Schema(nullable = true) LocalDateTime validUntil) {
}
