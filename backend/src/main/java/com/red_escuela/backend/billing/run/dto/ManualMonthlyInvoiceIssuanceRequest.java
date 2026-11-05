package com.red_escuela.backend.billing.run.dto;

import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;

public record ManualMonthlyInvoiceIssuanceRequest(@NotNull YearMonth period, @NotNull Long simulationRunId) {
}
