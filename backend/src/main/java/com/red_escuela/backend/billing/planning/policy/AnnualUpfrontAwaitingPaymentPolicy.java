package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_AWAITING_PAYMENT"}, decisions = {"D-160", "D-131"})
public final class AnnualUpfrontAwaitingPaymentPolicy implements InvoicePlanningPolicy<MonthlyInstallmentPlanningContext> {
    public String ruleName() {
        return "AnnualUpfrontAwaitingPaymentPolicy";
    }

    public int plannerQuestionNumber() {
        return 5;
    }

    public PolicyVerdict evaluateRule(MonthlyInstallmentPlanningContext context) {
        return context.facts().annualUpfrontAwaitsPaymentFor(context.period())
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_AWAITING_PAYMENT", "cuota " + context.period() + " reservada por un pedido de pago anual esperando pago")
                : PolicyVerdict.allow(ruleName());
    }
}
