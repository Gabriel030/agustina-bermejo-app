package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"REFUND_BLOCKED_BY_OVERDUE_DEBT"}, decisions = {"D-264"})
public final class RefundWithoutOverdueDebtPolicy implements Policy<RefundAvailabilityCheck> {

    @Override
    public String ruleName() {
        return "RefundWithoutOverdueDebtPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(RefundAvailabilityCheck check) {
        return check.hasOverdueDebt()
                ? PolicyVerdict.deny(ruleName(), "REFUND_BLOCKED_BY_OVERDUE_DEBT", "No se puede devolver saldo a favor mientras haya deuda vencida.")
                : PolicyVerdict.allow(ruleName());
    }
}
