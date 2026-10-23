package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PAYMENT_ALREADY_REFUNDED"}, decisions = {"D-262"})
public final class PaymentMoneyNotRefundedPolicy implements Policy<PaymentReversalCheck> {

    @Override
    public String ruleName() {
        return "PaymentMoneyNotRefundedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentReversalCheck check) {
        return check.refundedAmount().isZero()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "PAYMENT_ALREADY_REFUNDED", "El dinero de este pago ya se devolvió en parte: no se puede revocar ni contracargar.");
    }
}
