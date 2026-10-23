package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"INSUFFICIENT_ACCOUNT_CREDIT", "REFUND_EXCEEDS_AVAILABLE_AMOUNT"}, decisions = {"D-159", "D-264"})
public final class RefundNotGreaterThanAvailablePolicy implements Policy<RefundAvailabilityCheck> {

    @Override
    public String ruleName() {
        return "RefundNotGreaterThanAvailablePolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(RefundAvailabilityCheck check) {
        if (check.requestedAmount().isGreaterThan(check.availableCredit().value())) {
            return PolicyVerdict.deny(ruleName(), "INSUFFICIENT_ACCOUNT_CREDIT", "El monto pedido supera el saldo a favor disponible de la cuenta.");
        }
        if (check.requestedAmount().isGreaterThan(check.refundableInManualPayments().value())) {
            return PolicyVerdict.deny(ruleName(), "REFUND_EXCEEDS_AVAILABLE_AMOUNT",
                    "El monto pedido supera lo que se puede devolver de los pagos en efectivo o por transferencia.");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
