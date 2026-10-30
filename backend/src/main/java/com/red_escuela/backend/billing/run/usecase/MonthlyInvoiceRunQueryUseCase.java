package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;

import java.util.List;

public interface MonthlyInvoiceRunQueryUseCase {
    List<MonthlyInvoiceRunItemResponse> listMonthlyInvoiceRunItems(Long monthlyRunId, MonthlyInvoiceRunItemResult result);
}
