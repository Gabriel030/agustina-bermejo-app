package com.red_escuela.backend.billing.pricing.dto;

import org.springframework.lang.Nullable;
import com.red_escuela.backend.billing.pricing.domain.PriceChangeImpact;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record PriceChangeImpactResponse(LocalDate effectiveFrom, boolean reachesActiveEnrollments, long affectedStudents, @Nullable NonNegativeMoney currentFinalAmount,
                                        NonNegativeMoney proposedFinalAmount, Money currentMonthlyIncome, Money proposedMonthlyIncome,
                                        Money monthlyIncomeDifference, Money monthlyMarketingDiscountCost) {

    public static PriceChangeImpactResponse from(PriceChangeImpact impact) {
        return new PriceChangeImpactResponse(impact.effectiveFrom(), impact.reachesActiveEnrollments(), impact.affectedStudents(), impact.currentFinalAmount(),
                impact.proposedFinalAmount(), impact.currentMonthlyIncome(), impact.proposedMonthlyIncome(), impact.monthlyIncomeDifference(),
                impact.monthlyMarketingDiscountCost());
    }
}
