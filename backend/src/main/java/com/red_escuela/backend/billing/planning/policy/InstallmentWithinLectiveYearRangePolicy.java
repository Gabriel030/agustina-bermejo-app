package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"OUTSIDE_TERM", "YEAR_NOT_CONFIGURED"}, decisions = {"D-001", "D-006"})
public final class InstallmentWithinLectiveYearRangePolicy implements InvoicePlanningPolicy<MonthlyInstallmentPlanningContext> {
    public String ruleName() {
        return "InstallmentWithinLectiveYearRangePolicy";
    }

    public int plannerQuestionNumber() {
        return 5;
    }

    public PolicyVerdict evaluateRule(MonthlyInstallmentPlanningContext context) {
        return context.cycleResolution().installmentCycle().isPresent() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), context.cycleResolution().hasCoveringCycle() ? "OUTSIDE_TERM" : "YEAR_NOT_CONFIGURED", "sin cuota en " + context.facts().month());
    }
}
