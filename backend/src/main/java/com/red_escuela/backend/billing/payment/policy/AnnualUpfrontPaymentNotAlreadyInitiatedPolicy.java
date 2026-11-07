package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_PAYMENT_ALREADY_INITIATED"}, decisions = {"D-155"})
public final class AnnualUpfrontPaymentNotAlreadyInitiatedPolicy implements Policy<AnnualUpfrontReceiptCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontPaymentNotAlreadyInitiatedPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontReceiptCheck check) {
        return check.paymentAlreadyInitiated()
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_PAYMENT_ALREADY_INITIATED", "El pedido de pago anual ya tiene un pago iniciado.")
                : PolicyVerdict.allow(ruleName());
    }
}
