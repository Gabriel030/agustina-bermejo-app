package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"ANNUAL_UPFRONT_PAYMENT_EXPIRED"}, decisions = {"D-155", "D-521"})
public final class AnnualUpfrontPaymentNotExpiredPolicy implements Policy<AnnualUpfrontReceiptCheck> {

    @Override
    public String ruleName() {
        return "AnnualUpfrontPaymentNotExpiredPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(AnnualUpfrontReceiptCheck check) {
        return check.requestDiscarded()
                ? PolicyVerdict.deny(ruleName(), "ANNUAL_UPFRONT_PAYMENT_EXPIRED", "El pedido de pago anual ya venció o fue descartado: podés volver a pedirlo.")
                : PolicyVerdict.allow(ruleName());
    }
}
