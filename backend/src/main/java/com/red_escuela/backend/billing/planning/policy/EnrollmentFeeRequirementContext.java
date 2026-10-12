package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.domain.LectiveYearBillingTerms;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;

import java.util.Optional;

public record EnrollmentFeeRequirementContext(EnrollmentFeeRequirementScope scope, ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot,
                                              LectiveYearBillingTerms terms, boolean exemptionGranted, Optional<IssuedEnrollmentFee> issuedFee)
        implements ProgramFactsContext {

    public BillingConsultation consultation() {
        return BillingConsultation.valueOf(scope.name());
    }

    public String notIssuedDetail(String cause) {
        return "no se exige: " + cause;
    }
}
