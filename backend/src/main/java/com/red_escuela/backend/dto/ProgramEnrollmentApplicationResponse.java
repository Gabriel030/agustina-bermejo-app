package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.ProgramEnrollmentApplication;

import java.time.LocalDateTime;

// No se agrega un campo de "estado de pago" separado: la aprobación de la
// solicitud y la del PaymentReceipt asociado son atómicas (misma transacción,
// ver ProgramEnrollmentApplicationService.aprobar) — mientras la solicitud está
// PENDING, el comprobante siempre está PENDING_REVIEW; al aprobarse ambos pasan
// juntos. El único campo de UI que hace falta ya existe: `status`.
public record ProgramEnrollmentApplicationResponse(
        Long id,
        Long studentId,
        Long programId,
        String programName,
        Long programCohortId,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        String rejectionReason) {

    public static ProgramEnrollmentApplicationResponse from(ProgramEnrollmentApplication application) {
        return new ProgramEnrollmentApplicationResponse(
                application.getId(),
                application.getStudent().getId(),
                application.getProgram().getId(),
                application.getProgram().getName(),
                application.getProgramCohort() == null ? null : application.getProgramCohort().getId(),
                application.getStatus().name(),
                application.getRequestedAt(),
                application.getDecidedAt(),
                application.getRejectionReason());
    }
}
