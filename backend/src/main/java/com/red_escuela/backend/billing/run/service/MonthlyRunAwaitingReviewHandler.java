package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.invoice.domain.issuing.DraftInvoiceApprovalCommand;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.PreProcessingHandler;
import com.red_escuela.backend.billing.operation.PreProcessingResult;
import com.red_escuela.backend.billing.operation.PriorAuthorization;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotFoundException;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;

public class MonthlyRunAwaitingReviewHandler implements PreProcessingHandler<DraftInvoiceApprovalCommand> {
    private final MonthlyInvoiceRunRepository runRepository;

    public MonthlyRunAwaitingReviewHandler(MonthlyInvoiceRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public PreProcessingResult handleBeforeProcessing(BillingOperationType type, DraftInvoiceApprovalCommand command, PriorAuthorization priorAuthorization) {
        runRepository.findById(command.monthlyInvoiceRunId()).orElseThrow(() -> new MonthlyInvoiceRunNotFoundException(command.monthlyInvoiceRunId())).requireAwaitingReview();
        return PreProcessingResult.proceed();
    }
}
