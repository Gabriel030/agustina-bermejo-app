package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PLAN_COMPLETED"}, decisions = {"D-038"})
public final class StudyPlanNotCompletedPolicy implements InvoicePlanningPolicy<EligibilityPlanningContext> {
    public String ruleName() {
        return "StudyPlanNotCompletedPolicy";
    }

    public int plannerQuestionNumber() {
        return 1;
    }

    public PolicyVerdict evaluateRule(EligibilityPlanningContext context) {
        return !context.studyPlanCompleted() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "PLAN_COMPLETED", "el plan de estudios está completo");
    }
}
