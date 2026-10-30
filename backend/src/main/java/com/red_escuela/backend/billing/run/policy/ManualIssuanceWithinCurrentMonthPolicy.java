package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "MANUAL_ISSUANCE_OUTSIDE_CURRENT_MONTH", decisions = {"D-055", "D-161"})
public final class ManualIssuanceWithinCurrentMonthPolicy implements Policy<ManualIssuanceWithinCurrentMonthContext> {

    @Override
    public String ruleName() {
        return "ManualIssuanceWithinCurrentMonthPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ManualIssuanceWithinCurrentMonthContext context) {
        return context.requestedPeriod().equals(context.currentPeriod())
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "MANUAL_ISSUANCE_OUTSIDE_CURRENT_MONTH", "La emisión manual de recuperación solo corresponde al mes en curso");
    }
}
