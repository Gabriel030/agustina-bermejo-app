package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.time.temporal.ChronoUnit;

@PolicyRule(violationCodes = "INSTALLMENT_COUNT_EXCEEDS_TERM_SPAN", decisions = {"D-034"})
public final class InstallmentCountWithinTermSpanPolicy implements Policy<YearBillingConfigurationChange> {
    public String ruleName() {
        return "InstallmentCountWithinTermSpanPolicy";
    }

    public PolicyVerdict evaluateRule(YearBillingConfigurationChange change) {
        return (!change.hasTermDates() || change.proposed().recurringFeeInvoicesCount() == null || change.proposed().recurringFeeInvoicesCount() <= spanInMonths(change)) ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "INSTALLMENT_COUNT_EXCEEDS_TERM_SPAN", "La cantidad de cuotas no puede superar los meses del año lectivo.");
    }

    private static long spanInMonths(YearBillingConfigurationChange change) {
        return ChronoUnit.MONTHS.between(change.termStart().get(), change.termEnd().get()) + 1;
    }
}
