package com.red_escuela.backend.billing.configuration.policy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

public record YearBillingConfigurationChange(
        ProgramYearBillingTemplateSnapshot current,
        ProgramYearBillingTemplateSnapshot proposed,
        Optional<YearMonth> termStart,
        Optional<YearMonth> termEnd,
        Set<Integer> issuedEnrollmentFeeSlots,
        Set<YearMonth> issuedInstallmentPeriods,
        Optional<YearMonth> currentTermStart,
        Optional<LocalDate> enrollmentWindowClosesOn,
        boolean advanceEnrollmentFeeIssued) {

    public YearBillingConfigurationChange {
        issuedEnrollmentFeeSlots = Set.copyOf(issuedEnrollmentFeeSlots);
        issuedInstallmentPeriods = Set.copyOf(issuedInstallmentPeriods);
    }

    public YearBillingConfigurationChange(ProgramYearBillingTemplateSnapshot current, ProgramYearBillingTemplateSnapshot proposed,
                                          Optional<YearMonth> termStart, Optional<YearMonth> termEnd,
                                          Set<Integer> issuedEnrollmentFeeSlots, Set<YearMonth> issuedInstallmentPeriods) {
        this(current, proposed, termStart, termEnd, issuedEnrollmentFeeSlots, issuedInstallmentPeriods, Optional.empty());
    }

    public YearBillingConfigurationChange(ProgramYearBillingTemplateSnapshot current, ProgramYearBillingTemplateSnapshot proposed,
                                          Optional<YearMonth> termStart, Optional<YearMonth> termEnd,
                                          Set<Integer> issuedEnrollmentFeeSlots, Set<YearMonth> issuedInstallmentPeriods, Optional<YearMonth> currentTermStart) {
        this(current, proposed, termStart, termEnd, issuedEnrollmentFeeSlots, issuedInstallmentPeriods, currentTermStart, Optional.empty(), false);
    }

    public static YearBillingConfigurationChange forCreation(ProgramYearBillingTemplateSnapshot proposed,
                                                             Optional<YearMonth> termStart, Optional<YearMonth> termEnd) {
        return new YearBillingConfigurationChange(proposed, proposed, termStart, termEnd, Set.of(), Set.of());
    }

    public YearBillingConfigurationChange withEnrollmentWindowClosesOn(Optional<LocalDate> closesOn) {
        return new YearBillingConfigurationChange(current, proposed, termStart, termEnd, issuedEnrollmentFeeSlots, issuedInstallmentPeriods, currentTermStart,
                closesOn, advanceEnrollmentFeeIssued);
    }

    public YearBillingConfigurationChange withAdvanceEnrollmentFeeIssued(boolean issued) {
        return new YearBillingConfigurationChange(current, proposed, termStart, termEnd, issuedEnrollmentFeeSlots, issuedInstallmentPeriods, currentTermStart,
                enrollmentWindowClosesOn, issued);
    }

    public boolean hasTermDates() {
        return termStart.isPresent() && termEnd.isPresent();
    }
}
