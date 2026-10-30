package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;

import java.time.LocalDate;
import java.time.YearMonth;

public record ManualIssuanceRequiresSimulationContext(YearMonth requestedPeriod, LocalDate today, MonthlyInvoiceRun simulationRun) {
}
