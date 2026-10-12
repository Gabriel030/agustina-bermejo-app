package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"SUSPENDED_OR_WITHDRAWN"}, decisions = {"D-066", "D-053"})
public final class NotSuspendedOrWithdrawnForNonPaymentPolicy implements InvoicePlanningPolicy<EligibilityPlanningContext> {
    public String ruleName() {
        return "NotSuspendedOrWithdrawnForNonPaymentPolicy";
    }

    public int plannerQuestionNumber() {
        return 1;
    }

    public PolicyVerdict evaluateRule(EligibilityPlanningContext context) {
        return !context.suspendedOrWithdrawnForNonPayment() ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "SUSPENDED_OR_WITHDRAWN", "suspendida o dada de baja por falta de pago");
    }
}
