package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;

public record PendingBankTransferDecisionResult(Long paymentId, Long studentCurrentAccountId, LocalDate effectiveDate, NonNegativeMoney amount,
                                                List<Long> invoicesReleasedFromReview) {

    public PendingBankTransferDecisionResult {
        invoicesReleasedFromReview = List.copyOf(invoicesReleasedFromReview);
    }
}
