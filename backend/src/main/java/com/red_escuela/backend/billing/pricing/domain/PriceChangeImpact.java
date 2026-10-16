package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;

public record PriceChangeImpact(LocalDate effectiveFrom, boolean reachesActiveEnrollments, long affectedStudents, NonNegativeMoney currentFinalAmount,
                                NonNegativeMoney proposedFinalAmount, Money currentMonthlyIncome, Money proposedMonthlyIncome, Money monthlyIncomeDifference,
                                Money monthlyMarketingDiscountCost) {

    public static PriceChangeImpact of(LocalDate effectiveFrom, boolean reachesActiveEnrollments, long activeEnrollments, NonNegativeMoney currentFinalAmount,
                                       NonNegativeMoney proposedFinalAmount, NonNegativeMoney marketingDiscountAmount) {
        Objects.requireNonNull(proposedFinalAmount, "proposedFinalAmount");
        Objects.requireNonNull(marketingDiscountAmount, "marketingDiscountAmount");
        long affected = reachesActiveEnrollments ? activeEnrollments : 0;
        Money proposedIncome = proposedFinalAmount.value().multipliedBy(affected);
        Money currentIncome = currentFinalAmount == null ? Money.zero(proposedFinalAmount.value().currency()) : currentFinalAmount.value().multipliedBy(affected);
        return new PriceChangeImpact(effectiveFrom, reachesActiveEnrollments, affected, currentFinalAmount, proposedFinalAmount, currentIncome, proposedIncome,
                proposedIncome.minus(currentIncome), marketingDiscountAmount.value().multipliedBy(affected));
    }
}
