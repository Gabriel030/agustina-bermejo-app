package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NOT_REQUIRED_EXEMPTION_GRANTED"}, decisions = {"D-027"})
public final class EnrollmentFeeExemptionGrantedPolicy implements Policy<EnrollmentFeeRequirementContext> {
    public String ruleName() {
        return "EnrollmentFeeExemptionGrantedPolicy";
    }

    public PolicyVerdict evaluateRule(EnrollmentFeeRequirementContext context) {
        return !context.exemptionGranted() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "NOT_REQUIRED_EXEMPTION_GRANTED", "hay una habilitación vigente");
    }
}
