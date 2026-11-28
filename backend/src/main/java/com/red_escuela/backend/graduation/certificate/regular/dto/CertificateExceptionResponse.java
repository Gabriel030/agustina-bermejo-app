package com.red_escuela.backend.graduation.certificate.regular.dto;

import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.shared.domain.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CertificateExceptionResponse(Long id, Long studentId, RegularityKind kind, Long enrollmentId, OrganismKind organismKind,
                                           String organismReference, String reason, CertificateExceptionStatus status, Long requestedById,
                                           LocalDateTime requestedAt, @Schema(nullable = true) Long decidedById,
                                           @Schema(nullable = true) LocalDateTime decidedAt, @Schema(nullable = true) String decisionReason,
                                           @Schema(nullable = true) LocalDateTime validUntil, boolean createdByDirector,
                                           @Schema(nullable = true, description = "Deuda al momento del pedido; null si billing estaba apagado") Money debtTotalAtRequest,
                                           @Schema(nullable = true, description = "Cuotas vencidas al momento del pedido; null si billing estaba apagado") Integer overdueInstallmentsAtRequest,
                                           @Schema(nullable = true) String studentName, @Schema(nullable = true) String programName,
                                           @Schema(nullable = true) String requestedByName, @Schema(nullable = true) String decidedByName) {
}
