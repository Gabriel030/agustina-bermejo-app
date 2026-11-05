package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.util.List;

public record OverdueContact(Long studentId, int oldestDaysOverdue, int overdueInstallments, List<Money> overdueAmounts) {

    public OverdueContact {
        overdueAmounts = List.copyOf(overdueAmounts);
    }

    public boolean owesIn(Currency currency) {
        return overdueAmounts.stream().anyMatch(amount -> amount.currency() == currency);
    }
}
