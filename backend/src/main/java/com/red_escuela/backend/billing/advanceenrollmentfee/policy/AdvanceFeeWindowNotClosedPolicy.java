package com.red_escuela.backend.billing.advanceenrollmentfee.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_FEE_WINDOW_CLOSED", decisions = {"D-528", "D-529"})
public final class AdvanceFeeWindowNotClosedPolicy implements Policy<AdvanceEnrollmentFeeFacts> {
    public String ruleName() {
        return "AdvanceFeeWindowNotClosedPolicy";
    }

    public PolicyVerdict evaluateRule(AdvanceEnrollmentFeeFacts facts) {
        return !facts.today().isAfter(facts.closesOn()) ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_WINDOW_CLOSED", "Ya cerró la ventana de la matrícula del año siguiente.");
    }
}
