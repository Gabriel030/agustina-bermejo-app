package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;

public interface MonthlyInvoiceIssuanceStrategy {

    MonthlyInvoiceIssuanceMode mode();

    InvoiceIssuanceDestination destinationOfRun(Long monthlyInvoiceRunId);

    MonthlyInvoiceRunEvent finishEventOf(int failedItems, int issuedItems);
}
