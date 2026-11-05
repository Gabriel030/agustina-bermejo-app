package com.red_escuela.backend.billing.operation;

import java.util.List;

public record PendingTransferApprovalOutcome(Long paymentId, List<Long> receiptItemInvoiceIds, List<Long> settledInvoiceIds, List<Long> invoicesReleasedFromReview) {

    public PendingTransferApprovalOutcome {
        receiptItemInvoiceIds = List.copyOf(receiptItemInvoiceIds);
        settledInvoiceIds = List.copyOf(settledInvoiceIds);
        invoicesReleasedFromReview = List.copyOf(invoicesReleasedFromReview);
    }

    public boolean settledAnInvoiceOutsideTheReceipt() {
        return settledInvoiceIds.stream().anyMatch(invoiceId -> !receiptItemInvoiceIds.contains(invoiceId));
    }
}
