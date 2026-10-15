package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"OUTSIDE_TERM"}, decisions = {"D-039"})
public final class CourseInstallmentWithinTermPolicy implements InvoicePlanningPolicy<CourseInstallmentPlanningContext> {
    public String ruleName() {
        return "CourseInstallmentWithinTermPolicy";
    }

    public int plannerQuestionNumber() {
        return 5;
    }

    public PolicyVerdict evaluateRule(CourseInstallmentPlanningContext context) {
        return context.facts().termContains().test(context.period()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "OUTSIDE_TERM", "sin cuota en " + context.period());
    }
}
