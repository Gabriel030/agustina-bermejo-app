package com.red_escuela.backend.shared.domain;

import java.util.List;

public record BillingShutdownImpact(
        long openInvoices,
        long overdueInvoices,
        long pendingDraftInvoices,
        long invoicesAwaitingPayment,
        long invoicesUnderPaymentReview,
        long paymentsPendingReconciliation,
        long refundsInProgress,
        long runsInProgress,
        List<String> scheduledJobs) {

    public BillingShutdownImpact {
        scheduledJobs = List.copyOf(scheduledJobs);
    }
}
