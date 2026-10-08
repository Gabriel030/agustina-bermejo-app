package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"NO_PAYMENT_PLAN"}, decisions = {"D-050"})
public final class PaymentPlanChosenPolicy implements InvoicePlanningPolicy<EligibilityPlanningContext> {
    public String ruleName() {
        return "PaymentPlanChosenPolicy";
    }

    public int plannerQuestionNumber() {
        return 1;
    }

    public PolicyVerdict evaluateRule(EligibilityPlanningContext context) {
        return context.paymentPlanChosen() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "NO_PAYMENT_PLAN", "no eligió plan de pago");
    }
}
