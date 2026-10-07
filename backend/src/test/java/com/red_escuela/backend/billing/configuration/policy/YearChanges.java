package com.red_escuela.backend.billing.configuration.policy;

import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

final class YearChanges {
    static final YearMonth TERM_START = YearMonth.of(2027, 3);
    static final YearMonth TERM_END = YearMonth.of(2027, 12);

    private YearChanges() {
    }

    static ProgramYearBillingTemplateSnapshot snapshot(int count, int first, Integer second, Integer installments) {
        return new ProgramYearBillingTemplateSnapshot(count, first, second, installments);
    }

    static YearBillingConfigurationChange creation(ProgramYearBillingTemplateSnapshot proposed) {
        return YearBillingConfigurationChange.forCreation(proposed, Optional.of(TERM_START), Optional.of(TERM_END));
    }

    static YearBillingConfigurationChange withoutDates(ProgramYearBillingTemplateSnapshot proposed) {
        return YearBillingConfigurationChange.forCreation(proposed, Optional.empty(), Optional.empty());
    }

    static YearBillingConfigurationChange update(ProgramYearBillingTemplateSnapshot current, ProgramYearBillingTemplateSnapshot proposed,
                                                 Set<Integer> issuedSlots, Set<YearMonth> issuedPeriods) {
        return new YearBillingConfigurationChange(current, proposed, Optional.of(TERM_START), Optional.of(TERM_END), issuedSlots, issuedPeriods);
    }

    static YearBillingConfigurationChange termRange(YearMonth start, YearMonth end, Set<YearMonth> issuedPeriods) {
        ProgramYearBillingTemplateSnapshot same = snapshot(1, 2, null, 10);
        return new YearBillingConfigurationChange(same, same, Optional.of(start), Optional.of(end), Set.of(), issuedPeriods);
    }
}
