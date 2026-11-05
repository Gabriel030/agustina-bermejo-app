package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;

public final class DraftInvoiceIssuanceStrategy implements MonthlyInvoiceIssuanceStrategy {

    private final MonthlyInvoiceIssuanceStrategy fallbackWhenNothingToReview = new DirectInvoiceIssuanceStrategy();

    @Override
    public MonthlyInvoiceIssuanceMode mode() {
        return MonthlyInvoiceIssuanceMode.REVIEW;
    }

    @Override
    public InvoiceIssuanceDestination destinationOfRun(Long monthlyInvoiceRunId) {
        return new InvoiceIssuanceDestination.DraftOfMonthlyRun(monthlyInvoiceRunId);
    }

    @Override
    public MonthlyInvoiceRunEvent finishEventOf(int failedItems, int issuedItems) {
        return issuedItems > 0 ? MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_DRAFTS_PENDING_REVIEW : fallbackWhenNothingToReview.finishEventOf(failedItems, issuedItems);
    }
}
