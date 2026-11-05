package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.dto.RunPage;

import java.time.YearMonth;

public interface MonthlyInvoiceRunQueryUseCase {
    RunPage<MonthlyInvoiceRunResponse> listMonthlyInvoiceRuns(YearMonth period, Integer page, Integer size);

    MonthlyInvoiceRunResponse findMonthlyInvoiceRun(Long monthlyRunId);

    RunPage<MonthlyInvoiceRunItemResponse> listMonthlyInvoiceRunItems(Long monthlyRunId, MonthlyInvoiceRunItemResult result, Integer page, Integer size);
}
