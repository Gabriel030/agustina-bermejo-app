package com.red_escuela.backend.billing.advanceenrollmentfee.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_FEE_ENROLLMENT_NOT_ACTIVE", decisions = {"D-523"})
public final class AdvanceFeeEnrollmentActivePolicy implements Policy<AdvanceEnrollmentFeeFacts> {
    public String ruleName() {
        return "AdvanceFeeEnrollmentActivePolicy";
    }

    public PolicyVerdict evaluateRule(AdvanceEnrollmentFeeFacts facts) {
        return facts.enrollmentActive() ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_ENROLLMENT_NOT_ACTIVE", "La inscripción no está activa.");
    }
}
