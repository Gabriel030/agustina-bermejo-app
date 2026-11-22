package com.red_escuela.backend.graduation.procedure.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProcedureNextStep(ProcedureNextStepKind kind, String label, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String path) {
}
