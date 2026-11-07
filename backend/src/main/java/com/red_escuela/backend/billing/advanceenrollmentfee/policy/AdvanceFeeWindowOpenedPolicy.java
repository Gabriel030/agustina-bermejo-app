package com.red_escuela.backend.billing.advanceenrollmentfee.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_FEE_WINDOW_NOT_OPEN", decisions = {"D-528"})
public final class AdvanceFeeWindowOpenedPolicy implements Policy<AdvanceEnrollmentFeeFacts> {
    public String ruleName() {
        return "AdvanceFeeWindowOpenedPolicy";
    }

    public PolicyVerdict evaluateRule(AdvanceEnrollmentFeeFacts facts) {
        return !facts.today().isBefore(facts.opensOn()) ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_WINDOW_NOT_OPEN", "Todavía no abrió la matrícula del año siguiente.");
    }
}
