package com.red_escuela.backend.billing.advanceenrollmentfee.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_FEE_ALREADY_ISSUED", decisions = {"D-009", "D-523"})
public final class AdvanceFeeNotAlreadyIssuedPolicy implements Policy<AdvanceEnrollmentFeeFacts> {
    public String ruleName() {
        return "AdvanceFeeNotAlreadyIssuedPolicy";
    }

    public PolicyVerdict evaluateRule(AdvanceEnrollmentFeeFacts facts) {
        return !facts.alreadyIssued() ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_ALREADY_ISSUED", "La matrícula del año siguiente ya fue generada.");
    }
}
