package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record PaymentReversalCheck(Money creditedAmount, Money refundedAmount, Money pendingRefundAmount, Money requestedAmount, LocalDate paidOn, LocalDate chargedBackOn,
                                   LocalDate today, String reference) {

    public static PaymentReversalCheck ofRevocation(Money creditedAmount, Money refundedAmount, Money pendingRefundAmount) {
        return new PaymentReversalCheck(creditedAmount, refundedAmount, pendingRefundAmount, null, null, null, null, null);
    }
}
