package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ALREADY_ISSUED"}, decisions = {"D-009", "D-039"})
public final class MonthlyInstallmentNotAlreadyIssuedPolicy<C extends InstallmentPeriodContext> implements InvoicePlanningPolicy<C> {
    public String ruleName() {
        return "MonthlyInstallmentNotAlreadyIssuedPolicy";
    }

    public int plannerQuestionNumber() {
        return 7;
    }

    public PolicyVerdict evaluateRule(C context) {
        return !context.issuedPeriods().contains(context.period()) ? PolicyVerdict.allow(ruleName()) : PolicyVerdict.deny(ruleName(), "ALREADY_ISSUED", "cuota " + context.period());
    }
}
