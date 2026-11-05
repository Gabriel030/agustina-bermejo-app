package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.EnumSet;
import java.util.Set;

@PolicyRule(violationCodes = "NIGHTLY_BILLING_NOT_COMPLETED", decisions = {"D-055", "D-459"})
public final class NightlyBillingCompletedTodayPolicy implements Policy<NightlyBillingCompletedTodayContext> {
    private static final Set<NightlyBillingRunStatus> COMPLETED_STATUSES = EnumSet.of(NightlyBillingRunStatus.COMPLETED, NightlyBillingRunStatus.COMPLETED_WITH_ERRORS);

    @Override
    public String ruleName() {
        return "NightlyBillingCompletedTodayPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(NightlyBillingCompletedTodayContext context) {
        boolean completedToday = context.businessDate().equals(context.latestRunDate()) && COMPLETED_STATUSES.contains(context.latestRunStatus());
        return completedToday
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "NIGHTLY_BILLING_NOT_COMPLETED", "No hay una corrida nocturna completa para la fecha de hoy");
    }
}
