package com.red_escuela.backend.billing.run.dto;

import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;

public record MonthlyInvoiceSimulationRequest(@NotNull YearMonth period) {
}
