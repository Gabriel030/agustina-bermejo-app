package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NOT_ACTIVE"}, decisions = {"D-050"})
public final class AcademicallyActiveEnrollmentPolicy implements InvoicePlanningPolicy<EligibilityPlanningContext> {
    public String ruleName() {
        return "AcademicallyActiveEnrollmentPolicy";
    }

    public int plannerQuestionNumber() {
        return 1;
    }

    public PolicyVerdict evaluateRule(EligibilityPlanningContext context) {
        return context.academicallyActive() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "NOT_ACTIVE", context.subject().inactiveDetail());
    }
}
