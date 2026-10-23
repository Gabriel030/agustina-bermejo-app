package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;

public record PaymentReversalResult(Long paymentId, Long chargebackId, Long studentCurrentAccountId, PaymentStatus paymentStatus, NonNegativeMoney amount,
                                    LocalDate chargedBackOn, LocalDate effectiveDate, LocalDate reversedOn, String reference, List<Long> invoicesReopened,
                                    List<Long> invoicesSettled) {

    public PaymentReversalResult {
        invoicesReopened = List.copyOf(invoicesReopened);
        invoicesSettled = List.copyOf(invoicesSettled);
    }
}
