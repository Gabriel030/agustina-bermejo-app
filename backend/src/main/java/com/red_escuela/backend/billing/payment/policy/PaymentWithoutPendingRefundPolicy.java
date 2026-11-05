package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"PAYMENT_HAS_PENDING_REFUND"}, decisions = {"D-262", "D-265"})
public final class PaymentWithoutPendingRefundPolicy implements Policy<PaymentReversalCheck> {

    @Override
    public String ruleName() {
        return "PaymentWithoutPendingRefundPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PaymentReversalCheck check) {
        return check.pendingRefundAmount().isZero()
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "PAYMENT_HAS_PENDING_REFUND",
                        "El pago tiene una devolución pendiente: hay que rechazarla o darla por fallida antes de revocar o contracargar.");
    }
}
