package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;

public final class DirectInvoiceIssuanceStrategy implements MonthlyInvoiceIssuanceStrategy {

    @Override
    public MonthlyInvoiceIssuanceMode mode() {
        return MonthlyInvoiceIssuanceMode.DIRECT;
    }

    @Override
    public InvoiceIssuanceDestination destinationOfRun(Long monthlyInvoiceRunId) {
        return InvoiceIssuanceDestination.EMIT_NOW;
    }

    @Override
    public MonthlyInvoiceRunEvent finishEventOf(int failedItems, int issuedItems) {
        return failedItems == 0 ? MonthlyInvoiceRunEvent.RUN_FINISHED_CLEAN : MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_FAILURES;
    }
}
