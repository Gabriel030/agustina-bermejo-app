package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record PaymentVerification(Money verifiedAmount, LocalDate transferDate, String reviewNote) {

    private static final PaymentVerification NONE = new PaymentVerification(null, null, null);

    public static PaymentVerification none() {
        return NONE;
    }

    public boolean isEmpty() {
        return verifiedAmount == null && transferDate == null && reviewNote == null;
    }
}
