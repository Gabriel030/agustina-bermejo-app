package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontEligibility;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionResolution;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

public record AnnualUpfrontRequestPreparation(BillingEnrollmentFacts facts, AnnualUpfrontEligibility eligibility, Long lectiveYearTermId, List<YearMonth> periods,
                                              PriceVersionResolution price, Map<Integer, InvoiceDiscountCandidate> winners) {

    public AnnualUpfrontRequestPreparation {
        periods = List.copyOf(periods);
        winners = Map.copyOf(winners);
    }

    static AnnualUpfrontRequestPreparation rejected(BillingEnrollmentFacts facts, AnnualUpfrontEligibility eligibility) {
        return new AnnualUpfrontRequestPreparation(facts, eligibility, null, List.of(), null, Map.of());
    }

    static AnnualUpfrontRequestPreparation accepted(BillingEnrollmentFacts facts, AnnualUpfrontEligibility eligibility, Long lectiveYearTermId, List<YearMonth> periods,
                                                    PriceVersionResolution price, Map<Integer, InvoiceDiscountCandidate> winners) {
        return new AnnualUpfrontRequestPreparation(facts, eligibility, lectiveYearTermId, periods, price, winners);
    }
}
