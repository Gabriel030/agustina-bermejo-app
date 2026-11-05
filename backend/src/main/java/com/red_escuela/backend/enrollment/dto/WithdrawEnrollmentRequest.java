package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record WithdrawEnrollmentRequest(@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Fecha de la baja; si no viene, es hoy") LocalDate effectiveDate) {
}
