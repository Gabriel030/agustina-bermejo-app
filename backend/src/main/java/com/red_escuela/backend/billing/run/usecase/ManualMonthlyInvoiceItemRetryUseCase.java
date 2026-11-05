package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;

public interface ManualMonthlyInvoiceItemRetryUseCase {
    MonthlyInvoiceRunItemResponse retryMonthlyInvoiceRunItemManually(Long monthlyRunId, Long itemId);
}
