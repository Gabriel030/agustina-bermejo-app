package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;

public record PendingBankTransferDecisionResult(Long paymentId, Long studentCurrentAccountId, LocalDate effectiveDate, NonNegativeMoney amount,
                                                List<Long> invoicesReleasedFromReview, List<Long> invoicesSettled, List<Long> receiptItemInvoiceIds) {

    public PendingBankTransferDecisionResult {
        invoicesReleasedFromReview = List.copyOf(invoicesReleasedFromReview);
        invoicesSettled = List.copyOf(invoicesSettled);
        receiptItemInvoiceIds = List.copyOf(receiptItemInvoiceIds);
    }

    public PendingBankTransferDecisionResult(Long paymentId, Long studentCurrentAccountId, LocalDate effectiveDate, NonNegativeMoney amount,
                                             List<Long> invoicesReleasedFromReview) {
        this(paymentId, studentCurrentAccountId, effectiveDate, amount, invoicesReleasedFromReview, List.of(), List.of());
    }
}
