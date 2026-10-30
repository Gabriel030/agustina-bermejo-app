package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceSimulationResponse;

import java.time.YearMonth;

public interface MonthlyInvoiceSimulationUseCase {
    MonthlyInvoiceSimulationResponse simulateMonthlyInvoicesOfPeriodAsOfToday(YearMonth period);
}
