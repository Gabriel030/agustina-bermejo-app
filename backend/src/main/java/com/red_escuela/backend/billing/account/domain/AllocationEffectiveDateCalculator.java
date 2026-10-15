package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class AllocationEffectiveDateCalculator {

    private AllocationEffectiveDateCalculator() {
    }

    public static LocalDate calculateEffectiveDate(List<CreditContribution> creditsOldestFirst, NonNegativeMoney alreadyConsumedCredit,
                                                   NonNegativeMoney allocatedAmount, LocalDate fallbackDate) {
        Objects.requireNonNull(creditsOldestFirst, "creditsOldestFirst");
        Objects.requireNonNull(fallbackDate, "fallbackDate");
        Money target = alreadyConsumedCredit.value().plus(allocatedAmount.value());
        Money cumulative = Money.zero(target.currency());
        for (CreditContribution credit : creditsOldestFirst) {
            cumulative = cumulative.plus(credit.amount().value());
            if (!target.isGreaterThan(cumulative)) {
                return credit.effectiveDate();
            }
        }
        return fallbackDate;
    }
}
