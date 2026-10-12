package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PLAN_NOT_MONTHLY"}, decisions = {"D-040"})
public final class MonthlyCadencePolicy implements InvoicePlanningPolicy<MonthlyInstallmentPlanningContext> {
    public String ruleName() {
        return "MonthlyCadencePolicy";
    }

    public int plannerQuestionNumber() {
        return 5;
    }

    public PolicyVerdict evaluateRule(MonthlyInstallmentPlanningContext context) {
        return context.facts().cadenceAtMonth() == PaymentPlanCadence.MONTHLY ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "PLAN_NOT_MONTHLY", "el plan vigente es " + context.facts().cadenceAtMonth());
    }
}
