package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.time.LocalDate;

public record AnnualUpfrontEligibility(boolean eligible, PolicyViolation violation, Long openRequestInvoiceId, Integer lectiveYear, LocalDate requestDeadline,
                                       AnnualUpfrontQuote quote) {

    public static AnnualUpfrontEligibility notEligible(PolicyViolation violation, Long openRequestInvoiceId, Integer lectiveYear, LocalDate requestDeadline) {
        return new AnnualUpfrontEligibility(false, violation, openRequestInvoiceId, lectiveYear, requestDeadline, null);
    }

    public static AnnualUpfrontEligibility eligible(Integer lectiveYear, LocalDate requestDeadline, AnnualUpfrontQuote quote) {
        return new AnnualUpfrontEligibility(true, null, null, lectiveYear, requestDeadline, quote);
    }
}
