package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NOT_REQUIRED_SCOPE_NOT_BLOCKED"}, decisions = {"D-013"})
public final class EnrollmentFeeBlocksScopePolicy implements Policy<EnrollmentFeeRequirementContext> {
    public String ruleName() {
        return "EnrollmentFeeBlocksScopePolicy";
    }

    public PolicyVerdict evaluateRule(EnrollmentFeeRequirementContext context) {
        return context.terms().blocks(context.scope()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "NOT_REQUIRED_SCOPE_NOT_BLOCKED", "la matrícula impaga no bloquea " + context.scope());
    }
}
