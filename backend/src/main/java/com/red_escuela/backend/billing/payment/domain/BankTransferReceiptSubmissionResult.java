package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;

public record BankTransferReceiptSubmissionResult(Long receiptId, Long paymentId, Long studentCurrentAccountId, NonNegativeMoney declaredAmount,
                                                  LocalDate uploadDate, List<Item> items) {

    public BankTransferReceiptSubmissionResult {
        items = List.copyOf(items);
    }

    public record Item(Long invoiceId, int position, NonNegativeMoney outstandingAtSelection, boolean markedUnderReview) {
    }
}
