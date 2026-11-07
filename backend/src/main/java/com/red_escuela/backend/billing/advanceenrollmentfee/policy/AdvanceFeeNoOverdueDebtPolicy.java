package com.red_escuela.backend.billing.advanceenrollmentfee.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "ADVANCE_FEE_OVERDUE_DEBT", decisions = {"D-065", "D-128"})
public final class AdvanceFeeNoOverdueDebtPolicy implements Policy<AdvanceEnrollmentFeeFacts> {
    public String ruleName() {
        return "AdvanceFeeNoOverdueDebtPolicy";
    }

    public PolicyVerdict evaluateRule(AdvanceEnrollmentFeeFacts facts) {
        return !facts.overdueDebt() ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "ADVANCE_FEE_OVERDUE_DEBT", "Hay deuda vencida: regularizala antes de generar la matrícula del año siguiente.");
    }
}
