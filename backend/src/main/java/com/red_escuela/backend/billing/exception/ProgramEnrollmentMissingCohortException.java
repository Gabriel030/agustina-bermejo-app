package com.red_escuela.backend.billing.exception;

// Encontrado en auditoría (2026-09-03): antes de esto, ProgramEnrollmentBillingScheduler
// dependía de un NullPointerException implícito para este caso — un NPE no es un contrato de
// API. program_cohort_id sigue siendo nullable en la DB (ver ProgramEnrollmentService,
// comentario sobre los fixtures de Épica 2), así que este chequeo explícito es la única garantía
// real de la regla de negocio "sin cohorte no se genera ninguna cuota".
public class ProgramEnrollmentMissingCohortException extends RuntimeException {
    public ProgramEnrollmentMissingCohortException(Long programEnrollmentId) {
        super("Program enrollment " + programEnrollmentId + " has no cohort assigned — cannot generate its enrollment fee");
    }
}
