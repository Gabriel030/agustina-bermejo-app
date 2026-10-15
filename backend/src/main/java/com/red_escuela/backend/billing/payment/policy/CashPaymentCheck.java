package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record CashPaymentCheck(Money amount, LocalDate paidOn, LocalDate today, boolean requestedByDirector, String backdateReason, String receiptNumber,
                               String idempotencyKey) {

    public static CashPaymentCheck ofAmountOnly(Money amount) {
        return new CashPaymentCheck(amount, null, null, false, null, null, null);
    }
}
