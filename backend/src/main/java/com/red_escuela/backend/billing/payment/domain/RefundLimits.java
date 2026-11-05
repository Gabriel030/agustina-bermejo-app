package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.math.BigDecimal;

public final class RefundLimits {

    public static final int MAX_COMPLETION_REFERENCE_LENGTH = 120;
    public static final int MAX_FAILURE_REASON_LENGTH = 500;
    public static final int MAX_REJECTION_REASON_LENGTH = 500;
    private static final BigDecimal MAX_STORABLE_AMOUNT = new BigDecimal("9999999999.99");

    private RefundLimits() {
    }

    public static boolean isNotPositive(Money money) {
        return money.amount().signum() <= 0;
    }

    public static boolean exceedsStorableAmount(Money money) {
        return money.amount().compareTo(MAX_STORABLE_AMOUNT) > 0;
    }
}
