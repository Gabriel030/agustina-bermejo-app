package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CashPaymentLimits {

    public static final int ADMINISTRATIVE_BACKDATE_DAYS = 5;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    public static final int MAX_RECEIPT_NUMBER_LENGTH = 100;
    public static final int MAX_BACKDATE_REASON_LENGTH = 500;
    private static final String MAX_STORABLE_AMOUNT = "9999999999.99";

    private CashPaymentLimits() {
    }

    public static boolean isBeyondAdministrativeWindow(LocalDate paidOn, LocalDate today) {
        return paidOn.isBefore(today.minusDays(ADMINISTRATIVE_BACKDATE_DAYS));
    }

    public static boolean isNotPositive(Money money) {
        return money.amount().signum() <= 0;
    }

    public static boolean exceedsStorableAmount(Money money) {
        return money.amount().compareTo(new BigDecimal(MAX_STORABLE_AMOUNT)) > 0;
    }
}
