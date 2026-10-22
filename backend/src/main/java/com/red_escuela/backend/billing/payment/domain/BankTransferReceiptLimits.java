package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.math.BigDecimal;

public final class BankTransferReceiptLimits {

    public static final int MAX_OPERATION_NUMBER_LENGTH = 50;
    public static final int MAX_ORIGIN_ACCOUNT_LENGTH = 50;
    public static final int MAX_HOLDER_NAME_LENGTH = 120;
    public static final int MAX_BANK_NAME_LENGTH = 100;
    private static final BigDecimal MAX_STORABLE_AMOUNT = new BigDecimal("9999999999.99");

    private BankTransferReceiptLimits() {
    }

    public static boolean isNotPositive(Money money) {
        return money.amount().signum() <= 0;
    }

    public static boolean exceedsStorableAmount(Money money) {
        return money.amount().compareTo(MAX_STORABLE_AMOUNT) > 0;
    }
}
