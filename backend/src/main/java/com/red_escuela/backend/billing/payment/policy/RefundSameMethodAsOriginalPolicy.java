package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"REFUND_METHOD_MISMATCH"}, decisions = {"D-159", "D-209"})
public final class RefundSameMethodAsOriginalPolicy implements Policy<RefundCompletionCheck> {

    @Override
    public String ruleName() {
        return "RefundSameMethodAsOriginalPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(RefundCompletionCheck check) {
        return check.originalMethod() == check.declaredMethod()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "REFUND_METHOD_MISMATCH", "La devolución se registra por el mismo medio de pago del pago de origen.");
    }
}
