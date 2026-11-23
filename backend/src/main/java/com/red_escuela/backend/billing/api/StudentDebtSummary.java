package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

public record StudentDebtSummary(Money totalDue, int overdueInstallments) {

    public static StudentDebtSummary none() {
        return new StudentDebtSummary(Money.zero(Currency.ARS), 0);
    }

    public boolean hasDebt() {
        return overdueInstallments > 0;
    }
}
