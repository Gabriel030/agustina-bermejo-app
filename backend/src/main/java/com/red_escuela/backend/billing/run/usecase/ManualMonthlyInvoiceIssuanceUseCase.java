package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;

import java.time.YearMonth;

public interface ManualMonthlyInvoiceIssuanceUseCase {
    MonthlyInvoiceRunResponse issueMonthlyInvoicesManuallyAsOfToday(YearMonth period, Long simulationRunId);
}
