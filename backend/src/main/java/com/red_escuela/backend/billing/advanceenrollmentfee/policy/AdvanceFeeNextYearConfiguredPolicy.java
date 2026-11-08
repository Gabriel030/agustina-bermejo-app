package com.red_escuela.backend.billing.advanceenrollmentfee.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_FEE_NEXT_YEAR_NOT_CONFIGURED", decisions = {"D-532", "D-535"})
public final class AdvanceFeeNextYearConfiguredPolicy implements Policy<AdvanceEnrollmentFeeFacts> {
    public String ruleName() {
        return "AdvanceFeeNextYearConfiguredPolicy";
    }

    public PolicyVerdict evaluateRule(AdvanceEnrollmentFeeFacts facts) {
        return facts.nextYearConfigured() ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_NEXT_YEAR_NOT_CONFIGURED", "Falta la plantilla o el precio de la matrícula del año siguiente.");
    }
}
