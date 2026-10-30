package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunItemResponse;
import com.red_escuela.backend.billing.run.service.FailedMonthlyInvoiceItemRetryProcessor;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ManualMonthlyInvoiceItemRetryService implements ManualMonthlyInvoiceItemRetryUseCase {
    private final FailedMonthlyInvoiceItemRetryProcessor retryProcessor;

    public ManualMonthlyInvoiceItemRetryService(FailedMonthlyInvoiceItemRetryProcessor retryProcessor) {
        this.retryProcessor = retryProcessor;
    }

    @Override
    public MonthlyInvoiceRunItemResponse retryMonthlyInvoiceRunItemManually(Long monthlyRunId, Long itemId) {
        return MonthlyInvoiceRunItemResponse.of(retryProcessor.retryItemManually(monthlyRunId, itemId));
    }
}
