package com.red_escuela.backend.billing.operation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record PendingTransferApprovalOutcome(Long paymentId, List<Long> receiptItemInvoiceIds, List<Long> settledInvoiceIds, List<Long> invoicesReleasedFromReview) {

    public PendingTransferApprovalOutcome {
        receiptItemInvoiceIds = List.copyOf(receiptItemInvoiceIds);
        settledInvoiceIds = List.copyOf(settledInvoiceIds);
        invoicesReleasedFromReview = List.copyOf(invoicesReleasedFromReview);
    }

    public boolean settledAnInvoiceOutsideTheReceipt() {
        Set<Long> receiptInvoiceIds = new HashSet<>(receiptItemInvoiceIds);
        return settledInvoiceIds.stream().anyMatch(invoiceId -> !receiptInvoiceIds.contains(invoiceId));
    }
}
