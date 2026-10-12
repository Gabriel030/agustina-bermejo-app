package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"BEFORE_ENTRY"}, decisions = {"D-049"})
public final class NotBeforeEntryMonthPolicy implements InvoicePlanningPolicy<MonthlyInstallmentPlanningContext> {
    public String ruleName() {
        return "NotBeforeEntryMonthPolicy";
    }

    public int plannerQuestionNumber() {
        return 2;
    }

    public PolicyVerdict evaluateRule(MonthlyInstallmentPlanningContext context) {
        return !(context.facts().entryMonth() != null && context.facts().month().isBefore(context.facts().entryMonth())) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "BEFORE_ENTRY", "la cursada del alumno empieza en " + context.facts().entryMonth());
    }
}
