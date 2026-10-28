package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public record StudentCreditBalance(Currency currency, NonNegativeMoney unallocatedCredit, NonNegativeMoney reservedByPendingRefunds, NonNegativeMoney availableCredit) {

    public static StudentCreditBalance empty(Currency currency) {
        NonNegativeMoney zero = NonNegativeMoney.zero(currency);
        return new StudentCreditBalance(currency, zero, zero, zero);
    }
}
