package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.time.Month;
import java.time.YearMonth;
import java.util.Set;

@PolicyRule(violationCodes = "LECTIVE_YEAR_CALENDAR_NOT_SUPPORTED", decisions = {"D-524", "D-001"})
public final class LectiveYearCalendarSupportedPolicy implements Policy<YearBillingConfigurationChange> {
    private static final Set<Month> SUPPORTED_START_MONTHS = Set.of(Month.FEBRUARY, Month.MARCH);
    private static final Month REQUIRED_END_MONTH = Month.DECEMBER;

    public String ruleName() {
        return "LectiveYearCalendarSupportedPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (!change.hasTermDates() || isSupported(change.termStart().get(), change.termEnd().get())) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "LECTIVE_YEAR_CALENDAR_NOT_SUPPORTED", "El año lectivo debe empezar en febrero o en marzo y terminar en diciembre.");
    }

    private static boolean isSupported(YearMonth start, YearMonth end) {
        return SUPPORTED_START_MONTHS.contains(start.getMonth()) && end.getMonth() == REQUIRED_END_MONTH;
    }
}
