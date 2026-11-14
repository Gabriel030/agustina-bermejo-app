package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record WithdrawSectionEnrollmentRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Motivo de la baja (10 a 500 caracteres)") String reason) {
}
