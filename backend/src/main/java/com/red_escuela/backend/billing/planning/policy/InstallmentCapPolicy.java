package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INSTALLMENT_CAP_REACHED"}, decisions = {"D-034", "D-039"})
public final class InstallmentCapPolicy<C extends InstallmentCapContext> implements InvoicePlanningPolicy<C> {
    public String ruleName() {
        return "InstallmentCapPolicy";
    }

    public int plannerQuestionNumber() {
        return 5;
    }

    public PolicyVerdict evaluateRule(C context) {
        return !context.installmentCap().map(cap -> context.installmentsIssued() >= cap).orElse(false) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "INSTALLMENT_CAP_REACHED", context.installmentsIssued() + " de " + context.installmentCap().orElseThrow());
    }
}
