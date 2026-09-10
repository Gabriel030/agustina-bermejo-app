package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.NotNull;

// cohortId nullable: el alta directa por staff (ProgramEnrollmentController) puede
// no especificar cohorte — un alta manual de staff no está atada a la ventana de
// admisión de autoservicio (Épica 8 Fase 2). El flujo de autoservicio con pago
// (ProgramEnrollmentApplicationService.solicitarConPago) siempre la exige.
public record CreateProgramEnrollmentRequest(@NotNull Long programId, Long cohortId) {

    // Compat: callers existentes de alta directa por staff que no conocían cohortId.
    public CreateProgramEnrollmentRequest(Long programId) {
        this(programId, null);
    }
}
