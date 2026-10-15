package com.red_escuela.backend.billing.configuration.policy;

import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

public record YearBillingConfigurationChange(
        ProgramYearBillingTemplateSnapshot current,
        ProgramYearBillingTemplateSnapshot proposed,
        Optional<YearMonth> termStart,
        Optional<YearMonth> termEnd,
        Set<Integer> issuedEnrollmentFeeSlots,
        Set<YearMonth> issuedInstallmentPeriods) {

    public YearBillingConfigurationChange {
        issuedEnrollmentFeeSlots = Set.copyOf(issuedEnrollmentFeeSlots);
        issuedInstallmentPeriods = Set.copyOf(issuedInstallmentPeriods);
    }

    public static YearBillingConfigurationChange forCreation(ProgramYearBillingTemplateSnapshot proposed,
                                                             Optional<YearMonth> termStart, Optional<YearMonth> termEnd) {
        return new YearBillingConfigurationChange(proposed, proposed, termStart, termEnd, Set.of(), Set.of());
    }

    public boolean hasTermDates() {
        return termStart.isPresent() && termEnd.isPresent();
    }
}
