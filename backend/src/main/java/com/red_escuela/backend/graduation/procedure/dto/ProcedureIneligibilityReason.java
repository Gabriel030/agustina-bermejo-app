package com.red_escuela.backend.graduation.procedure.dto;

public record ProcedureIneligibilityReason(ProcedureIneligibilityCode code, String message, ProcedureNextStep nextStep) {
}
