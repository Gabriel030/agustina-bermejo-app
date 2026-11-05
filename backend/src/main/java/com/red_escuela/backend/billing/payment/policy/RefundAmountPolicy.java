package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RefundLimits;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"REFUND_AMOUNT_INVALID"}, decisions = {"D-159"})
public final class RefundAmountPolicy implements Policy<RefundRequestCheck> {

    @Override
    public String ruleName() {
        return "RefundAmountPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(RefundRequestCheck check) {
        boolean invalid = check.requestedAmount() == null || RefundLimits.isNotPositive(check.requestedAmount()) || RefundLimits.exceedsStorableAmount(check.requestedAmount());
        return invalid
                ? PolicyVerdict.deny(ruleName(), "REFUND_AMOUNT_INVALID", "El monto de la devolución debe ser mayor a cero y no superar 9.999.999.999,99.")
                : PolicyVerdict.allow(ruleName());
    }
}
